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

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.junit.jupiter.api.Assertions.*;

public class InternalProxyTest extends DistributionExtractingTestcase {

    private static final String BASE_URL = "http://localhost:2020";

    @Override
    protected String getExampleDirName() {
        return "internalproxy";
    }

    @Test
    public void testExpressOrderRoutesToInternalProxy() throws Exception {
        try(Process2 sl = startServiceProxyScript()) {
            // @formatter:off
            String response = given()
                    .contentType(XML)
                    .body(readFileFromBaseDir("express.xml"))
                .when()
                    .post(BASE_URL)
                .then()
                    .statusCode(200)
                    .extract()
                    .asString();
            // @formatter:on

            System.out.println("response = " + response);

            assertTrue(response.contains("Express processing!"));
        }
    }

    @Test
    public void testNonExpressOrderFallsThrough() throws Exception {
        try(Process2 sl = startServiceProxyScript()) {
            // @formatter:off
            String response = given()
                .when()
                    .get(BASE_URL)
                .then()
                    .statusCode(200)
                    .extract()
                    .asString();
            // @formatter:on

            assertTrue(response.contains("Normal processing!"));
        }
    }

    @Test
    public void testRegularOrderFallsThrough() throws Exception {
        String regularOrder = """
            <order express='no'>
                <items>
                    <item id="1" count="1"/>
                </items>
            </order>
            """;

        try(Process2 sl = startServiceProxyScript()) {
            // @formatter:off
            String response = given()
                    .contentType(XML)
                    .body(regularOrder)
                .when()
                    .post(BASE_URL)
                .then()
                    .statusCode(200)
                    .extract()
                    .asString();
            // @formatter:on

            assertTrue(response.contains("Normal processing!"));
        }
    }
}