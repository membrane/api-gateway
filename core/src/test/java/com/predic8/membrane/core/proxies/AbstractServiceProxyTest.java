package com.predic8.membrane.core.proxies;

import com.predic8.membrane.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static io.restassured.RestAssured.*;

@SuppressWarnings("UastIncorrectHttpHeaderInspection")
class AbstractServiceProxyTest extends AbstractTestWithRouter {

    @Test
    void getToPost() throws IOException {
        router.add(getBackend());
        router.add(getAPI());
        router.start();

        given()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .header("X-Called-Method", "POST");
    }

    private static @NotNull AbstractServiceProxy getAPI() {
        AbstractServiceProxy proxy = new AbstractServiceProxy() {};
        proxy.setKey(new ServiceProxyKey(2000));
        proxy.getInterceptors().add(A);

        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target() {
        };
        target.setMethod("POST");
        target.setHost("localhost");
        target.setPort(2010);

        proxy.setTarget(target);
        return proxy;
    }

    private static @NotNull APIProxy getBackend() {
        APIProxy p = new APIProxy();
        p.key = new APIProxyKey(2010);
        SetHeaderInterceptor sh = new SetHeaderInterceptor();
        sh.setFieldName("X-Called-Method");
        sh.setValue("${method}");
        p.getInterceptors().add(sh);
        p.getInterceptors().add(new ReturnInterceptor());
        return p;
    }

}