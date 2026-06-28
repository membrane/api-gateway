/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.tutorials.security;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class OAuth2ClientTokenRenewalTutorialTest extends DistributionExtractingTestcase {

    private static final String YAML = "55-OAuth2-Client-Token-Renewal.yaml";

    protected Process2 process;

    @Override
    protected String getExampleDirName() {
        return "../tutorials/security";
    }

    @Override
    protected String getParameters() {
        return "-c " + YAML;
    }

    /**
     * Runs after {@code DistributionExtractingTestcase.init()} sets {@code baseDir}.
     * Shortens the token lifetime from 60 s to 1 s so the test finishes quickly,
     * then starts Membrane with the patched config.
     */
    @BeforeEach
    void startGateway() throws IOException, InterruptedException {
        replaceInFile2(YAML, "expiration: 60", "expiration: 1");
        process = startServiceProxyScript();
    }

    @AfterEach
    void stopGateway() {
        if (process != null)
            process.killScript();
    }

    @Test
    void gatewayRenewsTokenAfterExpiry() throws InterruptedException {
        // @formatter:off
        String firstBody = given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Service accessed!"))
            .extract().body().asString();

        // Token lifetime is 1 s (patched from 60 s for test speed).
        // The client cache expires after ~0.9 s (1 s refresh buffer), so
        // sleeping 1 s is enough to force a new token fetch.
        Thread.sleep(1500);

        String secondBody = given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Service accessed!"))
            .extract().body().asString();
        // @formatter:on

        assertNotEquals(firstBody, secondBody,
                "oauth2Client must re-fetch a new token after the cached one expires");
    }
}
