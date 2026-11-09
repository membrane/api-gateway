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

package com.predic8.membrane.tutorials.scripting;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.equalTo;

public class GroovySignAndVerifyTutorialTest extends AbstractScriptingTutorialTest {

    private static final String SECRET = "test-secret";

    @Override
    protected String getTutorialYaml() {
        return "50-Groovy-Sign-And-Verify.yaml";
    }

    @Override
    protected Map<String, String> getEnvs() {
        return Map.of("SECRET", SECRET);
    }

    @Test
    void signedThroughSigner() {
        // @formatter:off
        given()
            .body("Hello")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(equalTo("Success"));
        // @formatter:on
    }

    @Test
    void validSignatureFromEnvSecret() throws Exception {
        // @formatter:off
        given()
            .header("X-Signature", hmac("Hello"))
            .body("Hello")
        .when()
            .post("http://localhost:2010")
        .then()
            .statusCode(200)
            .body(equalTo("Success"));
        // @formatter:on
    }

    @Test
    void tamperedBody() throws Exception {
        // @formatter:off
        given()
            .header("X-Signature", hmac("Hello"))
            .body("Hello!")
        .when()
            .post("http://localhost:2010")
        .then()
            .statusCode(401)
            .body(equalTo("Invalid Signature"));
        // @formatter:on
    }

    @Test
    void forgedSignature() {
        // @formatter:off
        given()
            .header("X-Signature", "0123abcd")
            .body("Hello")
        .when()
            .post("http://localhost:2010")
        .then()
            .statusCode(401)
            .body(equalTo("Invalid Signature"));
        // @formatter:on
    }

    @Test
    void notHex() {
        // @formatter:off
        given()
            .header("X-Signature", "not-hex")
            .body("Hello")
        .when()
            .post("http://localhost:2010")
        .then()
            .statusCode(401);
        // @formatter:on
    }

    @Test
    void missingSignature() {
        // @formatter:off
        given()
            .body("Hello")
        .when()
            .post("http://localhost:2010")
        .then()
            .statusCode(401);
        // @formatter:on
    }

    @Test
    void clientSignatureIsReplaced() {
        // @formatter:off
        given()
            .header("X-Signature", "0123abcd")
            .body("Hello")
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(equalTo("Success"));
        // @formatter:on
    }

    private static String hmac(String body) throws Exception {
        final var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(UTF_8)));
    }
}
