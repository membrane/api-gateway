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
package com.predic8.membrane.core;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import io.restassured.response.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;

public class OpenApiRewriteIntegrationTest {

    private static final Router r = new HttpRouter();

    @BeforeEach
    public void setUp() throws Exception {
        r.getRuleManager().addProxyAndOpenPortIfNew(getApiProxy());
        r.getRuleManager().addProxyAndOpenPortIfNew(getTargetProxy());
        r.init();
    }

    @AfterAll
    public static void tearDown() {
        r.shutdown();
    }

    @NotNull
    private static ServiceProxy getTargetProxy() throws Exception {
        ServiceProxy targetProxy = new ServiceProxy(new ServiceProxyKey("localhost", "GET", ".*", 3000), null, 8000);
        targetProxy.getInterceptors().add(new ReturnInterceptor());
        targetProxy.init(r);
        return targetProxy;
    }

    @NotNull
    private static APIProxy getApiProxy() throws Exception {
        APIProxy proxy = new APIProxy();
        OpenAPISpec spec = getSpec();
        Rewrite rw = new Rewrite();
        rw.setBasePath("/bar");
        spec.setRewrite(rw);
        spec.setValidateRequests(OpenAPISpec.YesNoOpenAPIOption.YES);
        proxy.setSpecs(singletonList(spec));
        proxy.setKey(new APIProxyKey(null, "*", 2000, null, "*", null, false));
        proxy.getInterceptors().add(new LogInterceptor());
        proxy.init(r);
        return proxy;
    }

    @Test
    void rewriteUrlInOpenAPIDocument() {
        Response r = given()
                .when()
                .get("http://localhost:2000/api-docs/rewriting-test-v1-0");

        assertTrue(r.asString().contains("http://localhost:2000/bar"));

        r.then().statusCode(200);
    }


    /**
     * The path /api/v2/foo in the OpenAPI should be rewritten to /bar/foo
     */
    @Test
    void rewriteURLInOpenAPI() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/bar/foo")
        .then()
            .statusCode(200);
        // @formatter:on
    }



    @NotNull
    private static OpenAPISpec getSpec() {
        OpenAPISpec s = new OpenAPISpec();
        s.location = "src/test/resources/openapi/specs/rewrite-integration-test.yml";
        return s;
    }
}
