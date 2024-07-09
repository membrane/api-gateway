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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.containsString;

public class APIProxyKeyIntegrationTest {

    private static Router router;

    @BeforeAll
    public static void setup() throws Exception {
        Rule restrictedRule = new APIProxy() {{setKey(new APIProxyKey("127.0.0.1", "localhost", 3000, "/foo", "*", null, false));}};
        restrictedRule.getInterceptors().add(new TemplateInterceptor() {{setTextTemplate("Baz");}});
        restrictedRule.getInterceptors().add(new ReturnInterceptor());
        restrictedRule.setName("Restricted Rule");

        Rule fallthroughRule = new APIProxy() {{setKey(new APIProxyKey("127.0.0.1", "localhost", 3000, null, "*", null, false));}};
        fallthroughRule.getInterceptors().add(new TemplateInterceptor() {{setTextTemplate("Foobar");}});
        fallthroughRule.getInterceptors().add(new ReturnInterceptor());
        fallthroughRule.setName("Fall through Rule");

        router = new HttpRouter();
        router.getRuleManager().addProxyAndOpenPortIfNew(restrictedRule);
        router.getRuleManager().addProxyAndOpenPortIfNew(fallthroughRule);
        router.init();
    }

    @Test
    void apiProxyPathMatchTest() {
        when()
            .get("http://localhost:3000/foo")
        .then()
            .body(containsString("Baz"));
    }

    @Test
    void apiProxyPathFallthroughTest() {
        when()
            .get("http://localhost:3000")
        .then()
            .body(containsString("Foobar"));
    }

    @AfterAll
    public static void shutdownRouter() throws IOException {
        router.shutdown();
    }
}
