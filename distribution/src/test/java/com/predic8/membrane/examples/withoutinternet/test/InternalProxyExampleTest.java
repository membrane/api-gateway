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
package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;
import static org.junit.jupiter.api.Assertions.*;

public class InternalProxyExampleTest extends DistributionExtractingTestcase {

    private static final String BASE_URL = "http://localhost:2000";

    @Override
    protected String getExampleDirName() {
        return "routing-traffic/internalproxy";
    }

    @Test
    public void testExpressOrderRoutesToInternalProxy() throws Exception {
        try(Process2 ignored = startServiceProxyScript()) {
            // @formatter:off
            given()
                .contentType(XML)
                .body(readFileFromBaseDir("express.xml"))
            .when()
                .post(BASE_URL)
            .then()
                .statusCode(200)
                .body(Matchers.containsStringIgnoringCase("Express"));


            String response = given()
                .when()
                    .body(readFileFromBaseDir("normal.xml"))
                    .post(BASE_URL)
                .then()
                    .statusCode(200)
                    .extract()
                    .asString();
            // @formatter:on

            assertTrue(response.contains("Normal processing!"));


            response = given()
                    .contentType(XML)
                    .body("""
                        <order express='no'>
                            <items>
                                <item id="1" count="1"/>
                            </items>
                        </order>
                        """)
                .when()
                    .post(BASE_URL)
                .then()
                    .statusCode(200)
                    .body(Matchers.containsStringIgnoringCase("Normal"))
                    .extract()
                    .asString();
            // @formatter:on

            assertTrue(response.contains("Normal processing!"));
        }
    }
}