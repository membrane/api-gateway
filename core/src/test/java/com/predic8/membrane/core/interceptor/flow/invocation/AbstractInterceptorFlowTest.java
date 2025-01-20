/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static io.restassured.RestAssured.*;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractInterceptorFlowTest {

    private static Router router;

    @BeforeAll
    static void setUp() {
        router = getRouter();
    }

    @AfterAll
    static void tearDown() {
        router.shutdown();
    }

    protected void assertFlow(String expected,Interceptor... interceptors) throws Exception {
        setUpRouter(interceptors);
        assertEquals(expected, given().post("http://localhost:2000").getBody().asString());
    }

    protected String getResponse(Interceptor... interceptors) throws Exception {
        setUpRouter(interceptors);
        return given().post("http://localhost:2000").getBody().asString();
    }

    private void setUpRouter(Interceptor[] interceptors) throws Exception {
        router.setRules(EMPTY_LIST);
        router.add(getApiProxy(interceptors));
        router.init();
    }

    private static @NotNull APIProxy getApiProxy(Interceptor[] interceptors) {
        APIProxy api = getServiceProxy();
        api.getInterceptors().addAll(Arrays.asList(interceptors));
        api.getInterceptors().add(new EchoInterceptor());
        return api;
    }

    @NotNull
    private static APIProxy getServiceProxy() {
        APIProxy api = new APIProxy();
        api.setKey(new ServiceProxyKey("*","*",null,2000));
        return api;
    }

    @NotNull
    private static Router getRouter() {
        Router r = new Router();
        r.setTransport(new HttpTransport());
        return r;
    }
}