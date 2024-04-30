package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.http.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static io.restassured.RestAssured.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class AbstractInterceptorFlowTest {

    protected abstract List<Interceptor> interceptors();

    protected abstract String flow();

    @Test
    void runFlow() throws Exception {
        Router router = getRouter();
        ServiceProxy api = getServiceProxy();

        List<Interceptor> i = api.getInterceptors();
        i.addAll(interceptors());
        i.add(new EchoInterceptor());
        router.add(api);
        router.init();
        call();
        router.shutdown();
    }

    private void call() {
        assertEquals(flow(),given().post("http://localhost:2000").getBody().asString());
    }

    @NotNull
    private static ServiceProxy getServiceProxy() {
        ServiceProxy api = new ServiceProxy();
        api.setKey(new ServiceProxyKey("*","*",null,2000));
        return api;
    }

    @NotNull
    private static Router getRouter() {
        Router router = new Router();
        router.setTransport(new HttpTransport());
        router.setExchangeStore(new ForgetfulExchangeStore());
        return router;
    }

}