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

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PostToGetTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-POST-to-GET.yaml";
    }

    @Test
    void verifyProductListIsSortedAfterPost() {
        var res =
                // @formatter:off
                given()
                    .contentType("application/json")
                    .body("""
                      {"limit":100,"sort":"name"}"""
                    )
                .when()
                    .post("http://localhost:2000/shop/v2/products")
                .then()
                    .statusCode(200)
                    .body("meta", notNullValue())
                    .body("meta.count", greaterThan(0))
                    .body("products", notNullValue())
                    .body("products.size()", greaterThan(0))
                    .body("products[0].name", not(empty()))
                    .body("products[0].self_link", startsWith("/shop/v2/products/"))
                    .extract();
                // @formatter:on

        List<String> names = res.jsonPath().getList("products.name", String.class);

        for (int i = 1; i < names.size(); i++) {
            String prev = names.get(i - 1);
            String cur  = names.get(i);
            if (prev == null || cur == null) continue;
            assertTrue(prev.compareToIgnoreCase(cur) <= 0);
        }
    }
}
