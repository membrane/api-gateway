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
package com.predic8.membrane.core.rules;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.containsString;

public class APIProxyKeyTest {

    private static Router router;

    @Test
    void apiProxyPathSubpathTest() throws Exception {
        registerRule("/foo", "Baz");
        router.init();
        when()
            .get("http://localhost:3000/foo/bar")
        .then()
            .body(containsString("Baz"));
    }

    @Test
    void apiProxyPathMatchTest() throws Exception {
        registerRule("/foo", "Baz");
        router.init();
        when()
            .get("http://localhost:3000/foo")
        .then()
            .body(containsString("Baz"));
    }

    @Test
    void apiProxyPathFallthroughTest() throws Exception {
        registerRule("/foo", "Baz");
        registerRule(null, "Foobar");
        router.init();
        when()
            .get("http://localhost:3000")
        .then()
            .body(containsString("Foobar"));
    }

    private static @NotNull void registerRule(String path, String body) throws IOException, ClassNotFoundException {
        router.getRuleManager().addProxyAndOpenPortIfNew(new APIProxy() {{
            setKey(new APIProxyKey("127.0.0.1", "localhost", 3000, path, "*", null, false));
            getInterceptors().add(new TemplateInterceptor() {{
                setTextTemplate(body);
            }});
            getInterceptors().add(new ReturnInterceptor());
        }});
    }

    @BeforeAll
    public static void setup() throws Exception {
        router = new HttpRouter();
    }

    @AfterAll
    public static void shutdownRouter() throws IOException {
        router.shutdown();
    }
}
