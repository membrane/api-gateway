/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.templating.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.*;
import org.hamcrest.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static io.restassured.RestAssured.*;

public class Swagger20Test {

    Router router;

    @BeforeEach
    public void setUp() throws Exception {

        router = new Router();
        router.setTransport(new HttpTransport());
        router.setUriFactory(new URIFactory());

        OpenAPISpec petstore_v2 = new OpenAPISpec();
        petstore_v2.location = "src/test/resources/openapi/specs/swagger-v2/petstore-v2.json";

        APIProxy proxy1 = createProxy(router, petstore_v2);
        proxy1.setTarget(new AbstractServiceProxy.Target("localhost", 2001));
        router.getRuleManager().addProxyAndOpenPortIfNew(proxy1);
        router.getRuleManager().addProxyAndOpenPortIfNew(getTargetProxy());
        router.init();
    }

    private @NotNull APIProxy getTargetProxy() throws Exception {
        APIProxy targetProxy = new APIProxy();
        targetProxy.setKey(new APIProxyKey(2001));
        targetProxy.getInterceptors().add(new StaticInterceptor() {{ textTemplate = "Hi!"; }});
        targetProxy.getInterceptors().add(new ReturnInterceptor());
        targetProxy.init(router);
        return targetProxy;
    }

    @AfterEach
    public void tearDown() {
        router.shutdown();
    }

    @Test
    void apiDocs() {
        // @formatter:off
        given()
            .get("http://localhost:2000/api-docs")
        .then()
            .body("swagger-petstore-v1-0-7.title", Matchers.equalTo("Swagger Petstore"));
        // @formatter:on
    }

    @Test
    void get() {
        // @formatter:off
        given()
            .get("http://localhost:2000/v2/pet/findByStatus?status=available")
        .then()
            .body(Matchers.equalTo("Hi!"));
        // @formatter:on
    }
}
