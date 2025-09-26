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
package com.predic8.membrane.core.interceptor.beautifier;

import com.predic8.membrane.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.Util.lineCount;
import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeautifierInterceptorIntegrationTest extends AbstractTestWithRouter {

    @BeforeEach
    void setup() throws IOException {

        APIProxy p = new APIProxy();
        p.setKey(new APIProxyKey(2000));

        RequestInterceptor req = new RequestInterceptor();
        req.getFlow().add(new BeautifierInterceptor());
        p.getFlow().add(req);
        p.getFlow().add(new ReturnInterceptor());
        router.add(p);
        router.start();
    }

    @Test
    void t1() {
        // @formatter:off
        String body = given()
            .body("""
                { "foo": { "boo": { "baz": "taz" }}}
                """)
            .contentType(APPLICATION_JSON)
            .post("http://localhost:2000/foo")
        .then()
            .log().ifValidationFails(ALL)
            .contentType(APPLICATION_JSON)
            .body("foo.boo.baz",equalTo("taz"))
            .statusCode(200)
            .extract().body().asPrettyString();
        // @formatter:on
        assertEquals(7, lineCount(body));
    }

}
