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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.templating.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static org.hamcrest.CoreMatchers.*;

public class APIProxyKeyTest {

    private static Router router;

    @BeforeAll
    public static void setup() {
        router = new HttpRouter();
    }

    @AfterAll
    public static void shutdown() {
        router.shutdown();
    }

    @Test
    void serviceProxyPathSubpath() throws Exception {
        registerApiProxy("/foo", "Baz");
        router.init();

        // @formatter:off
        when()
            .get("http://localhost:3000/foo/bar")
        .then()
            .log().ifValidationFails(ALL)
            .body(containsString("Baz"));
        // @formatter:on
    }

    @Test
    void apiProxyPathSubpath() throws Exception {
        registerApiProxy("/foo", "Baz");
        router.init();
        when()
            .get("http://localhost:3000/foo/bar")
        .then()
            .body(containsString("Baz"));
    }

    @Test
    void apiProxyPathMatch() throws Exception {
        registerApiProxy("/foo", "Baz");
        router.init();
        when()
            .get("http://localhost:3000/foo")
        .then()
            .body(containsString("Baz"));
    }

    @Test
    void apiProxyPathFallthrough() throws Exception {
        registerApiProxy("/foo", "Baz");
        registerApiProxy(null, "Foobar");
        router.init();
        when()
            .get("http://localhost:3000")
        .then()
            .body(containsString("Foobar"));
    }

    private static void registerApiProxy(String path, String body) throws IOException {
        router.getRuleManager().addProxyAndOpenPortIfNew(new APIProxy() {{
            setKey(new APIProxyKey("127.0.0.1", "localhost", 3000, path, "*", null,false));
            getInterceptors().add(new TemplateInterceptor() {{
                setSrc(body);
            }});
            if (path != null) {
                Path p = new Path();
                p.setUri(path);
                p.setRegExp(false);
                setPath(p);
            }
            getInterceptors().add(new ReturnInterceptor());
        }});
    }
}
