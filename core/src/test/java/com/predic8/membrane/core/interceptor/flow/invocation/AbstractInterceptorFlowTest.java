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
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.flow.invocation.testinterceptors.*;
import com.predic8.membrane.core.interceptor.misc.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.http.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static io.restassured.RestAssured.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class AbstractInterceptorFlowTest {

    public static Interceptor A = new FlowTestInterceptor("a");
    public static Interceptor B = new FlowTestInterceptor("b");
    public static Interceptor C = new FlowTestInterceptor("c");
    public static Interceptor D = new FlowTestInterceptor("d");
    public static Interceptor E = new FlowTestInterceptor("e");
    public static Interceptor I1 = new FlowTestInterceptor("i1");
    public static Interceptor I2 = new FlowTestInterceptor("i2");
    public static Interceptor I3 = new FlowTestInterceptor("i3");
    public static Interceptor I4 = new FlowTestInterceptor("i4");

    public static final Interceptor RETURN = new ReturnInterceptor();
    public static final Interceptor ABORT = new AbortFlowTestInterceptor();
    public static final Interceptor EXCEPTION = new ExceptionTestInterceptor();

    private static Router router;

    @BeforeAll
    static void setUp() throws Exception {
        router = getRouter();
    }

    @AfterAll
    static void tearDown() throws Exception {
        router.shutdown();
    }

    protected void assertFlow(String expected,Interceptor... interceptors) throws Exception {
        setUpRouter(interceptors);
        assertEquals(expected, given().post("http://localhost:2000").getBody().asString());
    }

    private void setUpRouter(Interceptor[] interceptors) throws Exception {
        router.setRules(EMPTY_LIST);
        router.add(getApiProxy(interceptors));
        router.init();
    }

    private static @NotNull APIProxy getApiProxy(Interceptor[] interceptors) {
        APIProxy api = getServiceProxy();
        api.getInterceptors().addAll(Arrays.asList(interceptors));
        api.getInterceptors().add(new EchoTestInterceptor());
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

    ConditionalInterceptor IF(String test,Interceptor... interceptors) {
        ConditionalInterceptor i = new ConditionalInterceptor();
        i.setTest(test);
        i.setInterceptors(asList(interceptors));
        return i;
    }

    RequestInterceptor REQUEST(Interceptor... interceptors) {
        RequestInterceptor ai = new RequestInterceptor();
        ai.setInterceptors(asList(interceptors));
        return ai;
    }

    ResponseInterceptor RESPONSE(Interceptor... interceptors) {
        ResponseInterceptor ai = new ResponseInterceptor();
        ai.setInterceptors(asList(interceptors));
        return ai;
    }

    AbortInterceptor ABORT(Interceptor... interceptors) {
        AbortInterceptor ai = new AbortInterceptor();
        ai.setInterceptors(asList(interceptors));
        return ai;
    }

    Interceptor I(String label) {
        return new FlowTestInterceptor(label);
    }
}