/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;

public class APIKeyWithOpenAPIExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "security/api-key/apikey-openapi";
    }

    @Test
    public void noApiKey() {
        when()
            .get("http://localhost:2000/shop/v2/products")
        .then().assertThat()
            .statusCode(401)
            .body(containsString("Authentication by API key is required."));
    }

    @Test
    public void noScopesGet() {
        given()
            .header("X-Api-Key", "111")
        .when()
            .get("http://localhost:2000/shop/v2/products")
        .then().assertThat()
            .statusCode(200)
            .body(containsString("meta"));
    }

    @Test
    public void noScopesPost() {
        given()
            .header("X-Api-Key", "111")
        .when()
            .post("http://localhost:2000/shop/v2/products")
        .then().assertThat()
            .statusCode(403)
            .body(containsString("Caller ist not in scope write"));
    }

    @Test
    public void writeScopes() {
        given()
            .headers("X-Api-Key", "222", "Content-Type", "application/json")
            .body("{\"name\": \"Mango\", \"price\": 2.79}")
        .when()
            .post("http://localhost:2000/shop/v2/products")
        .then().assertThat()
            .statusCode(201)
            .body(containsString("self_link"));
    }

}
