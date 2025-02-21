/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.Process2;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.IOException;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.test.StringAssertions.assertContains;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavascriptExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "scripting/javascript";
    }

    BufferLogger logger;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        logger = new BufferLogger();
        process = new Process2.Builder().in(baseDir).script("membrane").withWatcher(logger).waitForMembrane().start();

        // Dump HTTP
        //filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @Test
    public void returnJsonAsMap() {

        // @formatter:off
        Response r = get("http://localhost:2000?id=314&city=Boston");
        r.then().assertThat()
            .body("id",equalTo("314"))
            .body("city",equalTo("Boston"));
        // @formatter:on
    }

    @Test
    public void transformJson() throws JSONException {

        String jsonInput = """
                {
                    "id": 731,
                    "date": "7 Apr 2023",
                    "customer": 17,
                    "items": [
                        {
                            "quantity": 5,
                            "description": "Oolong",
                            "price": 5.90
                        },
                        {
                            "quantity": 2,
                            "description": "Assam",
                            "price": 2.95
                        },
                        {
                            "quantity": 1,
                            "description": "Darjeeling",
                            "price": 2.95
                        }
                    ]
                }""";

        Response response = given()
                .contentType(APPLICATION_JSON)
                .body(jsonInput)
                .post("http://localhost:2010");

        JSONAssert.assertEquals("""
                {
                    "id":731,
                    "date":"2023-04-07",
                    "client":17,
                    "total":38.35,
                    "positions": [
                        {
                            "pieces":5,
                            "price":5.90,
                            "article":"Oolong"
                        },
                        {
                            "pieces":2,
                            "price":2.95,
                            "article":"Assam"
                        },
                        {
                            "pieces":1,
                            "price":2.95,
                            "article":"Darjeeling"
                        }
                    ]
                }
                """, response.getBody().asString(),true);
    }

    @Test
    public void headers() {

        // @formatter:off
        given()
            .header("X-Foo",3141)
            .get("http://localhost:2020")
        .then().assertThat()
            .statusCode(200)
            .header("X-Javascript", "42");
        // @formatter:on

        assertContains("Request headers:",logger.toString());
        assertContains("X-Foo",logger.toString());
    }

    @Test
    public void springBeanResponse() {
        assertTrue(get("http://localhost:2030").getBody().asString().contains("Greetings from"));
    }

}