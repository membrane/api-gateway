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

package com.predic8.membrane.core.interceptor.flow.invocation.internalservice;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static io.restassured.RestAssured.*;

abstract class AbstractInternalServiceRoutingInterceptorTest {

    protected final CaptureRoutingTestInterceptor captureRoutingTestInterceptor = new CaptureRoutingTestInterceptor();

    private Router router;

    protected abstract void configure() throws Exception;

    @BeforeEach
    void setup() throws Exception {
        router = new HttpRouter();
        router.setHotDeploy(false);

        configure();

        router.init();
        router.start();
    }

    @AfterEach
    void tearDown() {
        router.shutdown();
    }

    public void api(Consumer<TestAPIProxy> c) throws Exception {
        TestAPIProxy api = new TestAPIProxy();
        c.accept(api);
        router.getRuleManager().addProxyAndOpenPortIfNew(api);
    }

    protected static class TestAPIProxy extends APIProxy {
        public void add(Interceptor... i) {
            getFlow().addAll(Arrays.asList(i));
        }
    }

    public void internal(Consumer<TestInternalProxy> c) throws Exception {
        TestInternalProxy api = new TestInternalProxy();
        c.accept(api);
        api.setKey(new InternalProxyKey());
        router.getRuleManager().addProxyAndOpenPortIfNew(api);
    }

    protected static class TestInternalProxy extends InternalProxy {
        public void add(Interceptor... i) {
            getFlow().addAll(Arrays.asList(i));
        }
    }

    protected String call() {
        return given().post("http://localhost:2000/original-path").getBody().asString();
    }

    protected static class CaptureRoutingTestInterceptor extends AbstractInterceptor {

        public String uri;
        public String destination;

        @Override
        public Outcome handleRequest(Exchange exc) {
            uri = exc.getRequest().getUri();
            if (!exc.getDestinations().isEmpty())
                destination = exc.getDestinations().getFirst();
            return CONTINUE;
        }
    }
}

