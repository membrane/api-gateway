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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static io.restassured.RestAssured.given;
import static io.restassured.filter.log.LogDetail.ALL;
import static org.hamcrest.Matchers.*;

class DefaultRouterTest {

    public static final String INTERNAL_SECRET = "supersecret";
    static Router dev, prod;
    static int prodPort, devPort;
    
    @BeforeAll
    static void setUp() throws IOException {
        prod = new DefaultRouter();
        dev = new DefaultRouter();
        prodPort = freePort();
        createRouter(prod, prodPort, true);
        devPort = freePort();
        createRouter(dev, devPort, false);
    }

    @AfterAll
    static void tearDown() {
        try {
            if (prod != null) prod.stop();
        } finally {
            if (dev != null) dev.stop();
        }
    }

    @Test
    void prodJson() {

        // @formatter:off
        given()
            .contentType(APPLICATION_JSON)
            .post("http://localhost:" + prodPort + "/")
        .then()
            .log().ifValidationFails()
            .statusCode(500)
            .contentType( containsString(APPLICATION_PROBLEM_JSON))
            .body("title", equalTo("Internal Server Error"))
            .body("type",equalTo("https://membrane-api.io/problems/internal"))
            .body("message", not(containsString(INTERNAL_SECRET)))
            .body("$",aMapWithSize(4));
        // @formatter:on
    }

    @Test
    void prodXML() {
        // @formatter:off
        given()
            .contentType(APPLICATION_XML)
            .post("http://localhost:" + prodPort + "/")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(500)
            .contentType(containsString(APPLICATION_PROBLEM_XML))
            .body("error.title", equalTo("Internal Server Error"))
            .body("error.type",equalTo("https://membrane-api.io/problems/internal"))
            .body("error.message", not(containsString(INTERNAL_SECRET)));
        // @formatter:on
    }

    @Test
    void  devJson() {
        // @formatter:off
        given()
            .get("http://localhost:" + devPort + "/")
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
                .post("http://localhost:" + devPort + "/")
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

    private static void createRouter(Router r, int port, boolean production) throws IOException {
        r.getConfiguration().setProduction(production);
        APIProxy api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                throw new RuntimeException(INTERNAL_SECRET);
            }

            @Override
            public String getDisplayName() {
                return "interceptor";
            }
        });
        r.getConfiguration().setHotDeploy(false);
        r.add(api);
        r.start();
    }
}