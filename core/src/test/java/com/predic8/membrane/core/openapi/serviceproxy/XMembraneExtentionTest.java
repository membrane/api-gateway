package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.parser.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.Assert.*;

public class XMembraneExtentionTest {

    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    OpenAPIPublisherInterceptor interceptor;

    Exchange get = new Exchange(null);

    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        Router router = new Router();
        router.setBaseLocation("");
        OpenAPIRecordFactory factory = new OpenAPIRecordFactory(router);
        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.setDir("src/test/resources/openapi/specs");
        List<OpenAPIRecord> records = factory.create(Collections.singletonList(spec));

        interceptor = new OpenAPIPublisherInterceptor(records);

        get.setRequest(new Request.Builder().method("GET").build());
    }

    @Test
    public void ids() {

        System.out.println("interceptor = " + interceptor.recs.keySet());

        interceptor.recs.get("extension-sample-1-4");
    }

}