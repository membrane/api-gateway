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

package com.predic8.membrane.tutorials.transformation;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class GetToPostTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "20-GET-to-POST.yaml";
    }

    @Test
    void addedProductIsPresentInUpstream() {
        var selfLink =
                // @formatter:off
                given()
                    .queryParam("name", "Lemon")
                    .queryParam("price", "0.30")
                .when()
                    .get("http://localhost:2000/add")
                .then()
                    .log().ifValidationFails()
                    .statusCode(201)
                    .body("id", notNullValue())
                    .body("name", equalTo("Lemon"))
                    .body("price", equalTo(0.3F))
                    .body("self_link", startsWith("/shop/v2/products/"))
                    .extract()
                    .path("self_link");
                // @formatter:on

        // @formatter:off
        given()
            .relaxedHTTPSValidation()
        .when()
            .get("https://api.predic8.de" + selfLink)
        .then()
            .statusCode(200)
            .body("name", equalTo("Lemon"))
            .body("price", equalTo(0.3F));
        // @formatter:on
    }

}
