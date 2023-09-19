package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAPIInterceptorProblemJson {

    Router router;

    OpenAPISpec spec;

    Exchange exc = new Exchange(null);

    OpenAPIInterceptor interceptor;

    @BeforeEach
    public void setUp() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/customers.yml";
        spec.setValidateRequests(OpenAPISpec.YesNoOpenAPIOption.YES);

        exc.setRequest(new Request.Builder().method("GET").build());

        interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);

    }

    @Test
    void post() throws Exception {

        Map<String,Object> customer = new HashMap<>();
        customer.put("id","CUST-7");
        customer.put("age",90);

        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("POST").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).body(convert2JSON(customer)).build());

        assertEquals(RETURN, interceptor.handleRequest(exc));

        ContentType ct = new ContentType(APPLICATION_PROBLEM_JSON);
        String contentType = exc.getResponse().getHeader().getContentType();
        System.out.println("contentType = " + contentType);
        assertTrue(ct.match(contentType));

        System.out.println("exc = " + exc.getResponse().getBodyAsStringDecoded());
    }
}
