package com.predic8.membrane.core;

import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static io.restassured.RestAssured.given;

public class OpenApiRewriteIntegrationTest {

    private static final Router r = new HttpRouter();

    @BeforeEach
    public void setUp() throws Exception {
        OpenAPISpec s = new OpenAPISpec();
        s.location = "src/test/resources/openapi/specs/oas_rewrite_integration_test.yml";

        APIProxy oasProxy = createProxy(r, s);
        OpenAPIInterceptor oai = new OpenAPIInterceptor(oasProxy, r);
        oasProxy.getInterceptors().add(oai);
        r.getRuleManager().addProxyAndOpenPortIfNew(oasProxy);

        Rule targetProxy = new ServiceProxy(new ServiceProxyKey("localhost", "GET", ".*", 3000), null, 8000);
        targetProxy.getInterceptors().add(new ReturnInterceptor());
        r.getRuleManager().addProxyAndOpenPortIfNew(targetProxy);

        r.init();
    }

    // @formatter:off
    @Test
    void simple()  {
        given()
        .when()
            .get("http://localhost:2000/foo")
        .then()
            .statusCode(200);
    }
    // @formatter:on

    @AfterAll
    public static void tearDown() throws Exception {
        r.shutdown();
    }
}
