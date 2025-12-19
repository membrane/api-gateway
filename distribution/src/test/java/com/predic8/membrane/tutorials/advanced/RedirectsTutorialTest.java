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

package com.predic8.membrane.tutorials.advanced;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;


public class RedirectsTutorialTest extends AbstractAdvancedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "50-Redirects.yaml";
    }

    @Test
    void redirects() {
        // @formatter:off
        given()
            .redirects().follow(false)
        .when()
            .get("http://localhost:2000/fruits/7")
        .then()
            .statusCode(301)
            .header("Location", equalTo("https://api.predic8.de/shop/v2/products/7"));
        // @formatter:on
    }
}