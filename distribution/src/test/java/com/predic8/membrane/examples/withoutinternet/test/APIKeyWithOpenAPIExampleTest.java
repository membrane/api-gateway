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

package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static org.hamcrest.Matchers.*;

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
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body(containsString("meta"));
    }

    @Test
    public void noScopesPost() {
        given()
            .header("X-Api-Key", "111")
            .contentType(APPLICATION_JSON)
            .body("""
                        {
                            "name": "Candy",
                            "price": 1.99
                        }""")
        .when()
            .post("http://localhost:2000/shop/v2/products")
        .then().assertThat()
            .log().ifValidationFails(ALL)
            .statusCode(403)
            .body("title", equalTo("OpenAPI message validation failed"))
            .body("type", equalTo("https://membrane-api.io/problems/user/validation"))
            .body("status", equalTo(403))
            .body("validation.method", equalTo("POST"))
            .body("validation.uriTemplate", equalTo("/products"))
            .body("validation.path", equalTo("/shop/v2/products"))
            .body("validation.errors.'REQUEST/'.size()", equalTo(1))
            .body("validation.errors.'REQUEST/'[0].message", equalTo("Caller is not in scope write"));
    }

    @Test
    public void writeScopes() {
        given()
            .headers("X-Api-Key", "222")
            .contentType(APPLICATION_JSON)
            .body("{\"name\": \"Mango\", \"price\": 2.79}")
        .when()
            .post("http://localhost:2000/shop/v2/products")
        .then().assertThat()
            .statusCode(201)
            .body(containsString("self_link"));
    }

}
