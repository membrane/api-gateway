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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

class ServiceProxyTest {

    private static Router router;

    @BeforeAll
    public static void setup() throws Exception {
        router = new HttpRouter();
        APIProxy proxyWithOutTarget = new APIProxy() {{
            key = new APIProxyKey(2000);
        }};
        router.add(proxyWithOutTarget);
        router.init();

    }

    @AfterAll
    public static void shutdown() {
        router.shutdown();
    }

    @Test
    void foo() {
        // @formatter:off
        when()
            .get("http://localhost:2000/foo")
        .then()
            .log().ifValidationFails()
            .statusCode(500)
            .contentType(APPLICATION_PROBLEM_JSON)
            .body("type", equalTo("https://membrane-api.io/error/internal/http-client"))
            .body("message", Matchers.containsString("/foo"))
            .body("message", Matchers.containsString("<target>"));
        // @formatter:on
    }
}