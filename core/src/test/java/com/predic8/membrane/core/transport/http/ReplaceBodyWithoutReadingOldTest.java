package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.flow.ResponseInterceptor;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.AbstractServiceProxy.Target;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

// TODO This is an attempt to recreate the error from the ConnectionKeepAliveTest without the openApiValidator
public class ReplaceBodyWithoutReadingOldTest {

    Router router;
    Router backend;

    @BeforeEach
    void setUp() throws Exception {
        router = getRouter();
        backend = getBackend();
    }

    @AfterEach
    public void tearDown() throws Exception {
        router.shutdown();
        backend.shutdown();
    }

    @Test
    void foo() throws Exception {
        HttpClient client = new HttpClient();
        Exchange exc = Request.get("http://localhost:2000/health").buildExchange();
        Exchange exc2 = Request.get("http://localhost:2000/health").buildExchange();
        client.call(exc);
        client.call(exc);
    }

    private @NotNull Router getRouter() throws Exception {
        HttpRouter router = new HttpRouter();
        router.getRuleManager().addProxyAndOpenPortIfNew(getOpenApiProxy());
        router.setBaseLocation("src/test/resources/");
        router.init();
        return router;
    }

    private @NotNull APIProxy getOpenApiProxy() {
        APIProxy apiProxy = new APIProxy();
        apiProxy.setPort(2000);
        apiProxy.setFlow(List.of(
                new ResponseInterceptor() {{
                    setFlow(List.of(
                            new GroovyInterceptor() {{
                                setSrc("""
                                        exchange.getResponse().setBody(new Body("Bar".getBytes()));
                                        """);
                            }}
                    ));
                }}
        ));
        apiProxy.setTarget(new Target("localhost", 3000));


        return apiProxy;
    }


    private Router getBackend() throws Exception{
        HttpRouter backend = new HttpRouter();
        backend.getRuleManager().addProxyAndOpenPortIfNew(new APIProxy() {{
            setPort(3000);
            setFlow(List.of(
                    new StaticInterceptor() {{
                        setSrc("Foo");
                    }},
                    new ReturnInterceptor()
            ));
        }});
        backend.init();
        return backend;
    }
}
