/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.shadowing;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.proxies.AbstractServiceProxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShadowingInterceptorTest {

    Exchange exc;
    Header header;

    static DefaultRouter interceptorRouter;
    static DefaultRouter shadowingRouter;

    static ServiceProxy interceptorProxy;
    static ShadowingInterceptor shadowingInterceptor;

    static ReturnInterceptor returnInterceptorMock;

    static ServiceProxy shadowingProxy;

    @BeforeEach
    void setUp() throws Exception {
        header = new Header() {{
            add(CONTENT_TYPE, APPLICATION_JSON);
        }};
        exc = ShadowingInterceptor.buildExchange(
                new Body("foo".getBytes()),
                new Request.Builder()
                        .post("https://www.google.com")
                        .header(header)
                        .buildExchange(),
                new Target() {{
                    setUrl("https://www.predic8.com:9000/foo");
                }},
                header
        );
    }

    @BeforeAll
    static void startup() throws Exception {
        interceptorRouter = new DefaultRouter();
        interceptorRouter.getConfiguration().setHotDeploy(false);
        interceptorRouter.setExchangeStore(new ForgetfulExchangeStore());
        interceptorRouter.setTransport(new HttpTransport());

        interceptorProxy = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), null, 0);
        shadowingInterceptor = new ShadowingInterceptor();
        shadowingInterceptor.setTargets(List.of(new Target() {{
            setHost("localhost");
            setPort(3000);
        }}));
        interceptorProxy.setFlow(List.of(
                shadowingInterceptor,
                new SetHeaderInterceptor() {{
                    setFieldName("foo");
                    setValue("bar");
                }},
                new ReturnInterceptor()
        ));

        interceptorRouter.add(interceptorProxy);
        interceptorRouter.start();

        shadowingRouter = new DefaultRouter();
        shadowingRouter.getConfiguration().setHotDeploy(false);
        shadowingRouter.setExchangeStore(new ForgetfulExchangeStore());
        shadowingRouter.setTransport(new HttpTransport());

        shadowingProxy = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3000), null, 0);
        returnInterceptorMock = Mockito.spy(new ReturnInterceptor());
        returnInterceptorMock.setStatus(200);
        shadowingProxy.setFlow(List.of(returnInterceptorMock));

        shadowingRouter.add(shadowingProxy);
        shadowingRouter.start();
    }

    @AfterAll
    static void shutdown() {
        shadowingRouter.stop();
        interceptorRouter.stop();
    }

    /**
     * Verifies that the shadow target is called by sending a request through the router
     * and ensures that the ReturnInterceptor's handleRequest() is invoked once.
     */
    @Test
    void testIfShadowTargetIsCalled() {
        given().when().get("http://localhost:2000").then().statusCode(200);
        verify(returnInterceptorMock, timeout(10000).times(1)).handleRequest(any(Exchange.class));
    }

    /**
     * Verifies that the shadow target is called and the ReturnInterceptor's
     * handleRequest() is invoked with an Exchange object not containing the "foo" header.
     */
    @Test
    void testIfShadowTargetHasFooHeader() {
        given().when().get("http://localhost:2000").then().statusCode(200);

        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
        verify(returnInterceptorMock, atLeastOnce()).handleRequest(exchangeCaptor.capture());

        assertNull(exchangeCaptor.getValue().getRequest().getHeader().getFirstValue("foo"));
    }


    @Test
    void buildExchangeTest() {
        assertNotNull(exc);
        assertEquals("POST", exc.getRequest().getMethod());
        assertEquals("/foo", exc.getRequest().getUri());
        assertEquals("https://www.predic8.com:9000/foo", exc.getDestinations().getFirst());
        assertEquals(APPLICATION_JSON, exc.getRequest().getHeader().getContentType());
    }
}
