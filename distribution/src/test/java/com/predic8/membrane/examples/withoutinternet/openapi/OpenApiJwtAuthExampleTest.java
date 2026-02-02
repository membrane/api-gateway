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

package com.predic8.membrane.examples.withoutinternet.openapi;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import static com.predic8.membrane.core.util.OSUtil.isWindows;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.*;

public class OpenApiJwtAuthExampleTest extends DistributionExtractingTestcase {

    private Process2 process;

    @Override
    protected String getExampleDirName() {
        return "openapi/jwt-auth";
    }

    @BeforeEach
    void setup() throws Exception {
        runGenerateJwk(getExampleDir(getExampleDirName()));
        process = startServiceProxyScript();
    }

    @AfterEach
    void stopMembrane() {
        if (process != null)
            process.killScript();
    }

    private static String fetchJwt() {
        // @formatter:off
        return given()
        .when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(200)
            .extract()
            .asString()
            .trim();
        // @formatter:on
    }

    @Test
    void shouldListProducts_whenValidJwtProvided() {
        String jwt = fetchJwt();
        // @formatter:off
        given()
            .header("Authorization", "Bearer " + jwt)
        .when()
            .get("http://localhost:2001/shop/v2/products")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("meta.count", greaterThan(0))
            .body("products", is(not(empty())))
            .body("products[0].id", notNullValue());
        // @formatter:on
    }

    @Test
    void shouldReturnSecurityProblem_whenMissingJwt() {
        // @formatter:off
        given()
            .accept(JSON)
        .when()
            .get("http://localhost:2001/shop/v2/products")
        .then()
            .statusCode(400)
            .body("type", equalTo("https://membrane-api.io/problems/security"))
            .body("detail", containsString("Could not retrieve JWT"));
        // @formatter:on
    }

    private static void runGenerateJwk(File dir) throws Exception {
        File jwk = new File(dir, "jwk.json");
        if (jwk.isFile() && jwk.length() > 0) return;

        Process p = createJwkProcess(dir);
        int exit = p.waitFor();

        if (exit != 0) {
            throw new IllegalStateException(MessageFormat.format("generate-jwk failed (exit {0}):\n{1}", exit, new String(p.getInputStream().readAllBytes(), UTF_8)));
        }
        if (!jwk.isFile() || jwk.length() == 0) {
            throw new IllegalStateException("generate-jwk did not create jwk.json in %s".formatted(dir.getAbsolutePath()));
        }
    }

    private static @NotNull Process createJwkProcess(File dir) throws IOException {
        File sh = new File(dir, "membrane.sh");
        File bat = new File(dir, "membrane.bat");

        ProcessBuilder pb;
        if (isWindows()) {
            String script = bat.exists() ? "membrane.bat" : "membrane";
            pb = new ProcessBuilder("cmd", "/c", script, "generate-jwk", "-o", "jwk.json");
        } else {
            String scriptPath = sh.exists() ? sh.getAbsolutePath() : new File(dir, "membrane").getAbsolutePath();
            pb = new ProcessBuilder("bash", scriptPath, "generate-jwk", "-o", "./jwk.json");
        }

        pb.directory(dir);
        pb.redirectErrorStream(true);

        return pb.start();
    }

}
