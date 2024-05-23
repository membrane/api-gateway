package com.predic8.membrane.core;

import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

public class OpenApiRewriteIntegrationTest {

    private static final Router r = new HttpRouter();

    @BeforeEach
    public void setUp() throws Exception {
        r.getRuleManager().addProxyAndOpenPortIfNew(getApiProxy());
        r.getRuleManager().addProxyAndOpenPortIfNew(getTargetProxy());
        r.init();
    }

    @NotNull
    private static Rule getTargetProxy() throws Exception {
        Rule targetProxy = new ServiceProxy(new ServiceProxyKey("localhost", "GET", ".*", 3000), null, 8000);
        targetProxy.getInterceptors().add(new ReturnInterceptor());
        targetProxy.init(r);
        return targetProxy;
    }

    @NotNull
    private static APIProxy getApiProxy() throws Exception {
        APIProxy proxy = new APIProxy();
        proxy.init(r);
        OpenAPISpec spec = getSpec();
        Rewrite rw = new Rewrite();
        rw.setUrl("/bar");
        spec.setRewrite(rw);
        spec.setValidateRequests(OpenAPISpec.YesNoOpenAPIOption.YES);
        proxy.setSpecs(singletonList(spec));
        proxy.setKey(new OpenAPIProxyServiceKey(null,"*",2000));
        proxy.getInterceptors().add(new OpenAPIInterceptor(proxy, r));
        return proxy;
    }

    @NotNull
    private static OpenAPISpec getSpec() {
        OpenAPISpec s = new OpenAPISpec();
        s.location = "src/test/resources/openapi/specs/rewrite-integration-test.yml";
        return s;
    }

    // @formatter:off
    @Test
    void simple()  {
        given()
        .when()
            .get("http://localhost:2000/bar/foo")
        .then()
            .statusCode(200);
    }
    // @formatter:on

    @AfterAll
    public static void tearDown() throws Exception {
        r.shutdown();
    }
}
