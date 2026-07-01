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

import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

public class OAuth2ClientTokenRenewalTutorialTest extends DistributionExtractingTestcase {

    private static final String YAML = "53-OAuth2-Client-Token-Renewal.yaml";

    /** The auth server logs this line whenever the gateway fetches a token. */
    private static final Pattern TOKEN_FETCH = Pattern.compile("POST /oauth2/token");
    /** The backend logs "Gateway forwarded token ...<last6>" for each forwarded request. */
    private static final Pattern FORWARDED_TOKEN = Pattern.compile("Gateway forwarded token \\.\\.\\.(\\S+)");

    protected Process2 process;
    private final BufferLogger logger = new BufferLogger();

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
     * Shortens the token lifetime from 60 s to 3 s so the test is quick. The client's
     * cache window is then ~2 s (refresh buffer = expiry/10, min 1 s), leaving a
     * comfortable margin so a reused token never validates right at its expiry.
     * Membrane's console is captured so we can read token fetches and the forwarded
     * token suffix from the log.
     */
    @BeforeEach
    void startGateway() throws IOException, InterruptedException {
        replaceInFile2(YAML, "expiration: 60", "expiration: 3");
        process = startServiceProxyScript(logger);
    }

    @AfterEach
    void stopGateway() {
        if (process != null)
            process.killScript();
    }

    @Test
    void reusesCachedTokenThenRenewsAfterExpiry() throws InterruptedException {
        // 1) First call: the gateway fetches a token, and the response never leaks it.
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Service accessed!"))
            .body(not(containsString("Bearer")));
        // @formatter:on
        Thread.sleep(200);
        int fetchesAfterFirstCall = countTokenFetches();
        String firstSuffix = lastForwardedSuffix();
        assertTrue(fetchesAfterFirstCall >= 1, "the gateway must fetch a token on the first call");

        // 2) Immediate second call: the cached token is reused — no new fetch, same token.
        given().when().get("http://localhost:2000").then().statusCode(200);
        Thread.sleep(200);
        assertEquals(fetchesAfterFirstCall, countTokenFetches(),
                "an immediate second call must reuse the cached token (no new POST /oauth2/token)");
        assertEquals(firstSuffix, lastForwardedSuffix(),
                "the reused token must be identical");

        // 3) After the cache window (~2 s) passes, the next call renews the token.
        Thread.sleep(2500);
        given().when().get("http://localhost:2000").then().statusCode(200);
        Thread.sleep(200);
        assertTrue(countTokenFetches() > fetchesAfterFirstCall,
                "after expiry the gateway must fetch a new token (POST /oauth2/token)");
        assertNotEquals(firstSuffix, lastForwardedSuffix(),
                "after expiry the forwarded token must change");
    }

    private int countTokenFetches() {
        int count = 0;
        Matcher m = TOKEN_FETCH.matcher(logger.toString());
        while (m.find())
            count++;
        return count;
    }

    private String lastForwardedSuffix() {
        String suffix = null;
        Matcher m = FORWARDED_TOKEN.matcher(logger.toString());
        while (m.find())
            suffix = m.group(1);
        return suffix;
    }
}
