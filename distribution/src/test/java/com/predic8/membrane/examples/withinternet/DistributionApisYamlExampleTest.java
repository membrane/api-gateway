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

package com.predic8.membrane.examples.withinternet;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DistributionApisYamlExampleTest extends AbstractSampleMembraneStartStopTestcase {


    @Override
    protected String getExampleDirName() {
        return "..";
    }

    @Override
    protected String getParameters() {
        return "-c conf/apis.yaml";
    }

    // @formatter:off
    @Test
    void defaultApi_returnsDynamicJson() {
        given()
        .when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(200)
            .header("Content-Type", containsString("application/json"))
            .body("$", hasKey("time"))
            .body("time", not(emptyOrNullString()));
    }

    @Test
    void factProxy_returnsFactJson() {
        given()
        .when()
            .get("http://localhost:2000/fact")
        .then()
            .statusCode(200)
            .header("Content-Type", containsString("application/json"))
            .body("$", hasKey("fact"))
            .body("fact", not(emptyOrNullString()));
    }

    @Test
    void apiDocs_isAvailable() {
        given()
        .when()
            .get("http://localhost:2000/api-docs")
        .then()
            .statusCode(200)
            .body(not(emptyOrNullString()));
    }

    @Test
    void rewrittenOpenAPIFromUI() {
        given()
            .get("http://localhost:2000/api-docs/fruit-shop-api-v2-2-0")
        .then()
            .body(containsString("http://localhost:2000/shop/v2"));
    }

    @Test
    void adminConsole_requiresBasicAuth() {
        given()
            .redirects().follow(false)
        .when()
            .get("http://localhost:9000/")
        .then()
            .statusCode(anyOf(is(401), is(403)));
    }

    @Test
    void adminConsole_acceptsBasicAuth() {
        given()
            .auth().preemptive().basic("admin", "admin")
            .redirects().follow(false)
        .when()
            .get("http://localhost:9000/")
        .then()
            .statusCode(anyOf(is(200), is(302), is(307)));
    }
    // @formatter:on
}
