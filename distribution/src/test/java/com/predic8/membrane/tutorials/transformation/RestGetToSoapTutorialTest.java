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

package com.predic8.membrane.tutorials.transformation;

import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class RestGetToSoapTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "40-REST-GET-to-SOAP.yaml";
    }

    @Test
    void restGetIsConvertedToSoapAndBackToJson() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/cities/Bielefeld")
        .then()
            .statusCode(200)
            .body("country", equalTo("Germany"))
            .body("population", greaterThan(0));
        // @formatter:on
    }
}
