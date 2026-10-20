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

import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.proxies.Target;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.util.NetworkTestUtil.freePorts;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static io.restassured.RestAssured.given;
import static io.restassured.filter.log.LogDetail.ALL;
import static java.util.Collections.singletonList;

public class Swagger20Test {

    Router router;
    int port, backendPort;

    @BeforeEach
    public void setUp() throws Exception {
        router = new TestRouter();
        int[] ports = freePorts(2);
        port = ports[0];
        backendPort = ports[1];
        router.add(getApiProxy());
        router.add(getTargetProxy());
        router.start();
    }

    private @NotNull APIProxy getApiProxy() {
        APIProxy ap = new APIProxy();
        ap.setOpenapi(singletonList(getOpenAPISpec()));
        ap.setKey(new APIProxyKey(port));
        ap.setTarget(new Target("localhost", backendPort));
        return ap;
    }

    private static @NotNull OpenAPISpec getOpenAPISpec() {
        OpenAPISpec petstore_v2 = new OpenAPISpec();
        petstore_v2.location = getPathFromResource("openapi/specs/swagger-v2/petstore-v2.json");
        return petstore_v2;
    }

    private @NotNull APIProxy getTargetProxy() {
        APIProxy tp = new APIProxy();
        tp.setKey(new APIProxyKey(backendPort));
        tp.getFlow().add(new StaticInterceptor() {{ src = "Hi!"; }});
        tp.getFlow().add(new ReturnInterceptor());
        tp.init(router);
        return tp;
    }

    @AfterEach
    public void tearDown() {
        router.stop();
    }

    @Test
    void apiDocs() {
        // @formatter:off
        given()
            .get("http://localhost:" + port + "/api-docs")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .body("swagger-petstore-v1-0-7.title", Matchers.equalTo("Swagger Petstore"));
        // @formatter:on
    }

    @Test
    void get() {
        // @formatter:off
        given()
            .get("http://localhost:" + port + "/v2/pet/findByStatus?status=available")
        .then()
            .body(Matchers.equalTo("Hi!"));
        // @formatter:on
    }
}
