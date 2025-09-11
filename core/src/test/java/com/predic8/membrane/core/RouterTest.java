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
package com.predic8.membrane.core;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static org.hamcrest.Matchers.*;

class RouterTest {

    public static final String INTERNAL_SECRET = "supersecret";
    static Router dev, prod;
    
    @BeforeAll
    static void setUp() throws IOException {
        prod = createRouter(2000, true);
        dev = createRouter(2001, false);
    }

    @AfterAll
    static void tearDown() {
        prod.shutdown();
        dev.shutdown();
    }

    @Test
    void prodJson() {

        // @formatter:off
        given()
            .contentType(APPLICATION_JSON)
            .post("http://localhost:2000/")
        .then()
            .log().ifValidationFails()
            .statusCode(500)
            .contentType( containsString(APPLICATION_PROBLEM_JSON))
            .body("title", equalTo("Internal server error."))
            .body("type",equalTo("https://membrane-api.io/problems/internal"))
            .body("message", not(containsString(INTERNAL_SECRET)))
            .body("$",aMapWithSize(3));
        // @formatter:on
    }

    @Test
    void prodXML() {
        // @formatter:off
        given()
            .contentType(APPLICATION_XML)
            .post("http://localhost:2000/")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(500)
            .contentType(containsString(APPLICATION_PROBLEM_XML))
            .body("error.title", equalTo("Internal server error."))
            .body("error.type",equalTo("https://membrane-api.io/problems/internal"))
            .body("error.message", not(containsString(INTERNAL_SECRET)));
        // @formatter:on
    }

    @Test
    void  devJson() {
        // @formatter:off
        given()
            .get("http://localhost:2001/")
        .then()
            .statusCode(500)
            .contentType(containsString(APPLICATION_PROBLEM_JSON))
            .body("title", equalTo("Internal server error."))
            .body("type",equalTo("https://membrane-api.io/problems/internal"))
            .body("$",hasKey("attention"))
            .body("attention", containsString("development mode"))
            .body("$",hasKey("message"))
            .body("$",not(hasKey("stacktrace")));
        // @formatter:on
    }

    @Test
    void devXML() {
        // @formatter:off
        given()
                .contentType(APPLICATION_XML)
                .post("http://localhost:2001/")
            .then()
                .log().ifValidationFails(ALL)
                .statusCode(500)
                .contentType(containsString(APPLICATION_PROBLEM_XML))
                .body("problem-details.title", equalTo("Internal server error."))
                .body("problem-details.type",equalTo("https://membrane-api.io/problems/internal"))
                .body("problem-details.attention", containsString("development mode"))
                .body("problem-details.message", containsString("supersecret"))
                .body("problem-details.stacktrace", not(containsString("HttpServerHandler")));
        // @formatter:on
    }

    private static Router createRouter(int port, boolean production) throws IOException {
        HttpRouter r = new HttpRouter();
        r.setProduction(production);
        APIProxy api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                throw new RuntimeException(INTERNAL_SECRET);
            }

            @Override
            public String getDisplayName() {
                return "interceptor";
            }
        });
        r.setHotDeploy(false);
        r.add(api);
        r.start();
        return r;
    }
}