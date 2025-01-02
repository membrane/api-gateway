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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.containsString;

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
    void serviceProxyPathSubpathTest() throws Exception {
        registerApiProxy("/foo", "Baz");
        router.init();
        when()
                .get("http://localhost:3000/foo/bar")
                .then()
                .body(containsString("Baz"));
    }

    @Test
    void apiProxyPathSubpathTest() throws Exception {
        registerApiProxy("/foo", "Baz");
        router.init();
        when()
            .get("http://localhost:3000/foo/bar")
        .then()
            .body(containsString("Baz"));
    }

    @Test
    void apiProxyPathMatchTest() throws Exception {
        registerApiProxy("/foo", "Baz");
        router.init();
        when()
            .get("http://localhost:3000/foo")
        .then()
            .body(containsString("Baz"));
    }

    @Test
    void apiProxyPathFallthroughTest() throws Exception {
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
            setKey(new APIProxyKey("127.0.0.1", "localhost", 3000, path, "*", null, false));
            getInterceptors().add(new TemplateInterceptor() {{
                setTextTemplate(body);
            }});
            if (path != null) {
                Path p = new Path();
                p.setValue(path);
                p.setRegExp(false);
                setPath(p);
            }
            getInterceptors().add(new ReturnInterceptor());
        }});
    }
}
