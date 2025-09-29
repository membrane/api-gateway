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

import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.log.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import org.hamcrest.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static java.util.Collections.*;

public class OpenApiRewriteIntegrationTest {

    private final Router r = new HttpRouter();

    @BeforeEach
    public void setUp() throws Exception {
        r.getRuleManager().addProxyAndOpenPortIfNew(getApiProxy());
        r.getRuleManager().addProxyAndOpenPortIfNew(getTargetProxy());
        r.init();
    }

    @AfterEach
    public void tearDown() {
        r.shutdown();
    }

    @NotNull
    private ServiceProxy getTargetProxy() {
        ServiceProxy tp = new ServiceProxy(new ServiceProxyKey("localhost", "GET", ".*", 3000), null, 8000);
        tp.getFlow().add(new ReturnInterceptor());
        tp.init(r);
        return tp;
    }

    @NotNull
    private static APIProxy getApiProxy() {
        APIProxy proxy = new APIProxy();
        OpenAPISpec spec = getSpec();
        Rewrite rw = new Rewrite();
        rw.setBasePath("/bar");
        spec.setRewrite(rw);
        spec.setValidateRequests(OpenAPISpec.YesNoOpenAPIOption.YES);
        proxy.setSpecs(singletonList(spec));
        proxy.setKey(new APIProxyKey(null, "*", 2000, null, "*", null,false));
        proxy.getFlow().add(new LogInterceptor());
      //  proxy.init(r);
        return proxy;
    }

    @Test
    void rewriteUrlInOpenAPIDocument() {
        // @formatter:off
        given()
            .when()
                .get("http://localhost:2000/api-docs/rewriting-test-v1-0")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body(Matchers.containsString("http://localhost:2000/bar"));
        // @formatter:on
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
        s.location = getPathFromResource("openapi/specs/rewrite-integration-test.yml");
        return s;
    }
}
