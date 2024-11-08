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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.misc.SetHeaderInterceptor;
import com.predic8.membrane.core.rules.AbstractServiceProxy.Target;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShadowingInterceptorTest {

    Exchange exc;
    Header header;

    static Router interceptorRouter;
    static Router shadowingRouter;

    static Rule interceptorRule;
    static ShadowingInterceptor shadowingInterceptor;

    static ReturnInterceptor returnInterceptorMock;

    static Rule shadowingRule;

    @BeforeEach
    void setUp() throws Exception {
        header = new Header(){{
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
        interceptorRouter = new Router();
        interceptorRouter.setHotDeploy(false);
        interceptorRouter.setExchangeStore(new ForgetfulExchangeStore());
        interceptorRouter.setTransport(new HttpTransport());

        interceptorRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), null, 0);
        shadowingInterceptor = new ShadowingInterceptor();
        shadowingInterceptor.setTargets(List.of(new Target() {{
            setHost("localhost");
            setPort(3000);
        }}));
        interceptorRule.setInterceptors(List.of(
                shadowingInterceptor,
                new SetHeaderInterceptor() {{
                    setName("foo");
                    setValue("bar");
                }},
                new ReturnInterceptor()
        ));

        interceptorRouter.getRuleManager().addProxyAndOpenPortIfNew(interceptorRule);
        interceptorRouter.init();
        interceptorRouter.start();

        shadowingRouter = new Router();
        shadowingRouter.setHotDeploy(false);
        shadowingRouter.setExchangeStore(new ForgetfulExchangeStore());
        shadowingRouter.setTransport(new HttpTransport());

        shadowingRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3000), null, 0);
        returnInterceptorMock = Mockito.spy(new ReturnInterceptor());
        returnInterceptorMock.setStatusCode(200);
        shadowingRule.setInterceptors(List.of(returnInterceptorMock));

        shadowingRouter.getRuleManager().addProxyAndOpenPortIfNew(shadowingRule);
        shadowingRouter.init();
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
    void testIfShadowTargetIsCalled() throws Exception {
        given().when().get("http://localhost:2000").then().statusCode(200);
        verify(returnInterceptorMock, times(1)).handleRequest(any(Exchange.class));
    }

    /**
     * Verifies that the shadow target is called and the ReturnInterceptor's
     * handleRequest() is invoked with an Exchange object not containing the "foo" header.
     */
    @Test
    void testIfShadowTargetHasFooHeader() throws Exception {
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
        assertEquals("https://www.predic8.com:9000/foo", exc.getDestinations().get(0));
        assertEquals(APPLICATION_JSON, exc.getRequest().getHeader().getContentType());
    }
}