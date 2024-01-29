/* Copyright 2024 predic8 GmbH, www.predic8.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */
package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;

public class APIKeyTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "security/api-key";
    }

    @Test
    public void notAuthenticated() {
        when()
            .get("http://localhost:2000")
        .then().assertThat()
            .statusCode(401);
    }

    @Test
    public void notAuthorized() {
        given()
            .header("X-Api-Key", "98765")
        .when()
            .get("http://localhost:2000")
        .then().assertThat()
            .statusCode(403);
    }

    @Test
    public void successKeyHeader() {
        given()
            .header("X-Api-Key", "P8MBR")
        .when()
            .get("http://localhost:2000")
        .then().assertThat()
            .statusCode(200);
    }

    @Test
    public void successQueryKey() {
        given()
            .queryParam("api-key", "P8MBR")
        .when()
            .get("http://localhost:2000")
        .then().assertThat()
            .statusCode(200);
    }

    @Test
    public void normalScope() {
        given()
            .header("X-Key", "123456789")
        .when()
            .get("http://localhost:3000")
        .then().assertThat()
            .statusCode(200)
            .body(equalTo("Normal Area"));
    }

    @Test
    public void conditionalScope() {
        given()
            .header("X-Key", "key_321_abc")
        .when()
            .get("http://localhost:3000")
        .then().assertThat()
            .statusCode(200)
            .body(equalTo("Secret Area!"));
    }
}
