/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples.tests.message_transformation;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.hamcrest.Matchers.*;

public class TransformationUsingJavascriptTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "message-transformation/transformation-using-javascript";
    }

    @Test
    public void simpleTransformation() throws Exception {
        BufferLogger logger = new BufferLogger();
        try(Process2 ignored = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().withWatcher(logger).start()) {

            // @formatter:off
            given()
                    .contentType(JSON)
                    .body("""
                        {
                            "from": "Hong Kong",
                            "to": "Hanoi"
                        }""")
            .when()
                    .post("http://localhost:2000/flight")
            .then().assertThat()
                    .statusCode(200)
            .body("flight",equalTo("Hong Kong to Hanoi"));
            // @formatter:on
        }
    }

    @Test
    public void complexTransformation() throws Exception {
        BufferLogger logger = new BufferLogger();
        try(Process2 ignored = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().withWatcher(logger).start()) {

            // @formatter:off
            given()
                    .contentType(JSON)
                    .body(readFileFromBaseDir("order.json"))
            .when()
                    .post("http://localhost:2000/orders")
            .then().assertThat()
                    .statusCode(200)
                    .body("total",equalTo(31.22F))
                    .body("number",equalTo(324))
                    .body("positions[0].product",equalTo("Tea"))
                    .body("positions[0].pieces",equalTo(2))
                    .body("positions[0].amount",equalTo(1.87F));
            // @formatter:on
        }
    }
}