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

package com.predic8.membrane.tutorials.openapi.v32;

import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.containsString;

public class ItemSchemaTutorialTest extends AbstractOpenAPIV32TutorialTest {

    // RestAssured does not know the application/jsonl content type; send its body verbatim as text.
    final RestAssuredConfig jsonlConfig = config().encoderConfig(
            encoderConfig().encodeContentTypeAs("application/jsonl", ContentType.TEXT));

    @Override
    protected String getTutorialYaml() {
        return "20-itemSchema.apis.yaml";
    }

    @Test
    void validStreamIsForwarded() {
        // @formatter:off
        given()
            .config(jsonlConfig)
            .contentType("application/jsonl")
            .body("{\"id\":\"1\",\"title\":\"First\"}\n{\"id\":\"2\",\"title\":\"Second\"}")
        .when()
            .post("http://localhost:2000/documents")
        .then()
            .statusCode(202);
        // @formatter:on
    }

    @Test
    void itemViolatingItemSchemaIsRejected() {
        // @formatter:off
        given()
            .config(jsonlConfig)
            .contentType("application/jsonl")
            .body("{\"id\":\"1\",\"title\":\"First\"}\n{\"id\":\"2\"}")
        .when()
            .post("http://localhost:2000/documents")
        .then()
            .statusCode(400)
            .body(containsString("/1/title"));
        // @formatter:on
    }
}
