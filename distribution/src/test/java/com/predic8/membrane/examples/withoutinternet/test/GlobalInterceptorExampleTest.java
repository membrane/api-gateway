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

package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.Header.*;
import static io.restassured.RestAssured.*;

public class GlobalInterceptorExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "/extending-membrane/global-interceptor";
    }

    // @formatter:off
    @Test
    void request1() {
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .assertThat()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST")
            .header("Access-Control-Allow-Headers", CONTENT_TYPE)
            .statusCode(200);
    }

    @Test
    void request2() {
        given()
        .when()
            .get("http://localhost:2001")
        .then()
            .assertThat()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST")
            .header("Access-Control-Allow-Headers", CONTENT_TYPE)
            .statusCode(404);
    }
    // @formatter:on
}