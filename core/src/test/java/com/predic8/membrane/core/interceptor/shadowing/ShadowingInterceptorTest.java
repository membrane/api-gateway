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
package com.predic8.membrane.core.interceptor.shadowing;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.lang.SetHeaderInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.proxies.Target;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static io.restassured.RestAssured.given;
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

    /**
     * A request body that failed to read is incomplete: shadowing it would send a truncated request to
     * the targets, so the clone must be suppressed.
     */
    @Test
    void failedRequestBodyIsNotShadowed() throws Exception {
        List<AbstractBody> shadowed = new ArrayList<>();
        ShadowingInterceptor interceptor = new ShadowingInterceptor() {
            @Override
            public void cloneRequestAndSend(AbstractBody completeBody, Exchange mainExchange, Header copiedHeader) {
                shadowed.add(completeBody);
            }
        };
        Exchange exchange = new Request.Builder().post("http://localhost:2000").buildExchange();
        Body body = new Body(ThrowingInputStream.closedChannel("partial"), 1000);
        exchange.getRequest().setBody(body);

        interceptor.handleRequest(exchange);
        assertThrows(ReadingBodyException.class, body::read);

        assertTrue(shadowed.isEmpty());
    }

    @Test
    void completeRequestBodyIsShadowed() throws Exception {
        List<AbstractBody> shadowed = new ArrayList<>();
        ShadowingInterceptor interceptor = new ShadowingInterceptor() {
            @Override
            public void cloneRequestAndSend(AbstractBody completeBody, Exchange mainExchange, Header copiedHeader) {
                shadowed.add(completeBody);
            }
        };
        Exchange exchange = new Request.Builder().post("http://localhost:2000").buildExchange();
        Body body = new Body(new ByteArrayInputStream("foo".getBytes()), 3);
        exchange.getRequest().setBody(body);

        interceptor.handleRequest(exchange);
        body.read();

        assertEquals(List.of(body), shadowed);
    }
}
