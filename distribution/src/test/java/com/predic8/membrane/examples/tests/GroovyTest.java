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

import com.predic8.membrane.examples.util.*;
import io.restassured.filter.log.*;
import io.restassured.response.*;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.test.AssertUtils.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class GroovyTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "groovy";
    }

    BufferLogger logger;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        logger = new BufferLogger();
        process = new Process2.Builder().in(baseDir).script("service-proxy").withWatcher(logger).waitForMembrane().start();

        // Dump HTTP
        filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @Test
    public void returnJsonAsMap() {
        get("http://localhost:2000").then().assertThat()
                .body("id",equalTo(7))
                .body("city",equalTo("Berlin"));
    }

    @Test
    public void transformJson() {

        String jsonStr = """
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
                .body(jsonStr)
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
        given()
                .header("X-Foo",3141)
                .get("http://localhost:2020")
        .then().assertThat()
                .statusCode(200)
                .header("X-Groovy", "42");

        assertContains("Request headers:",logger.toString());
        assertContains("X-Foo",logger.toString());
    }

    @Test
    public void springBeanResponse() {
        assertTrue(get("http://localhost:2030").getBody().asString().contains("Greetings from Spring"));
    }
}