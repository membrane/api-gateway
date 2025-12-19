/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.IsNull.notNullValue;

public class OpenApiValidationTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "90-OpenAPI-Validation.yaml";
    }

    @Test
    void createProductNegativePriceFailsValidation() {
        // @formatter:off
        given()
            .contentType("application/json")
            .body("""
            { "name": "Figs", "price": -2.7 }
            """)
        .when()
            .post("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(is(400))
            .body("title", equalTo("OpenAPI message validation failed"))
            .body("validation.errors", notNullValue())
            .body("validation.errors.size()", greaterThan(0));
        // @formatter:on
    }

}
