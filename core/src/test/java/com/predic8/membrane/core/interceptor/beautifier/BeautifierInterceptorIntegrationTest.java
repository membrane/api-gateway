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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static org.hamcrest.Matchers.equalTo;

public class BeautifierInterceptorIntegrationTest extends AbstractTestWithRouter {

    @BeforeEach
    void setup() throws IOException {

        APIProxy p = new APIProxy();
        p.setKey(new APIProxyKey(2000));

        RequestInterceptor req = new RequestInterceptor();
        req.getInterceptors().add(new BeautifierInterceptor());
        p.getInterceptors().add(req);
        p.getInterceptors().add(new ReturnInterceptor());
        router.add(p);
        router.start();
    }

    @Test
    void t1() {
        // @formatter:off
        given()
            .body("""
                { "foo": { "boo": { "baz": "taz" }}}
                """)
            .contentType(APPLICATION_JSON)
            .post("http://localhost:2000/foo")
        .then()
            .log().ifValidationFails(ALL)
            .contentType(APPLICATION_JSON)
         //   .header(CONTENT_LENGTH,"59")
                .body("foo.boo.baz",equalTo("taz"))
            .statusCode(200);
        // @formatter:on

        // TODO body split by LF StringUtils lineCount
    }

}
