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

package com.predic8.membrane.tutorials.soap;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.equalTo;

public class Wsdl2OpenAPITutorialTest extends AbstractSOAPTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "95-WSDL-to-OpenAPI.yaml";
    }

    @Test
    void backendWsdl() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2001/city-service?wsdl")
        .then()
            .statusCode(200)
            .contentType(XML);
        // @formatter:on
    }

    @Test
    void apiDocs() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/api-docs")
        .then()
            .statusCode(200);
        // @formatter:on
    }

    @Test
    void getCity() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"name\":\"Berlin\"}")
        .when()
            .post("http://localhost:2000/service/get-city")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("country", equalTo("Germany"))
            .body("population", equalTo(3897000));
        // @formatter:on
    }
}
