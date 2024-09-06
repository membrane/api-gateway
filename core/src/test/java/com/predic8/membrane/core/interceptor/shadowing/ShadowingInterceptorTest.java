package com.predic8.membrane.core.interceptor.shadowing;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.rules.AbstractServiceProxy.Target;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ShadowingInterceptorTest {

    Exchange exc;
    Header header;

    static Router router;
    static Router router2;

    static Rule shadowingRule;
    static ShadowingInterceptor shadowingInterceptor;

    static ReturnInterceptor returnInterceptorMock;

    static Rule staticRule;

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
                }}
        );
    }

    @BeforeAll
    static void startup() throws Exception {
        router = new Router();
        router.setHotDeploy(false);
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.setTransport(new HttpTransport());

        shadowingRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), null, 0);
        shadowingInterceptor = new ShadowingInterceptor();
        shadowingInterceptor.setTargets(List.of(new Target() {{
            setHost("localhost");
            setPort(3000);
        }}));
        shadowingRule.setInterceptors(List.of(shadowingInterceptor, new ReturnInterceptor()));

        router.getRuleManager().addProxyAndOpenPortIfNew(shadowingRule);
        router.init();
        router.start();

        router2 = new Router();
        router2.setHotDeploy(false);
        router2.setExchangeStore(new ForgetfulExchangeStore());
        router2.setTransport(new HttpTransport());

        staticRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3000), null, 0);
        returnInterceptorMock = Mockito.spy(new ReturnInterceptor());
        returnInterceptorMock.setStatusCode(200);
        staticRule.setInterceptors(List.of(returnInterceptorMock));

        router2.getRuleManager().addProxyAndOpenPortIfNew(staticRule);
        router2.init();
        router2.start();
    }

    @AfterAll
    static void shutdown() {
        router2.stop();
        router.stop();
    }

    @Test
    void interceptorTest() throws Exception {
        getAndAssert200("http://localhost:2000");
        verify(returnInterceptorMock, times(1)).handleRequest(any(Exchange.class));
    }

    @Test
    void buildExchangeTest() {
        assertNotNull(exc);
        assertNotSame(header, exc.getRequest().getHeader());
        assertEquals("POST", exc.getRequest().getMethod());
        assertEquals("/foo", exc.getRequest().getUri());
        assertEquals("https://www.predic8.com:9000/foo", exc.getDestinations().get(0));
        assertEquals(APPLICATION_JSON, exc.getRequest().getHeader().getContentType());
    }
}