package com.predic8.membrane.core.openapi.util;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;

import java.io.*;
import java.util.*;

import static java.util.Collections.singletonList;

public class TestUtils {

    public static final ObjectMapper om = new ObjectMapper();

    public static InputStream toInputStrom(String s) {
        return new ByteArrayInputStream(s.getBytes());
    }

    public static InputStream getResourceAsStream(Object obj, String fileName) {
        return obj.getClass().getResourceAsStream(fileName);
    }

    public static OpenAPIProxy createProxy(Router router, OpenAPIProxy.Spec spec) throws Exception {
        OpenAPIProxy proxy = new OpenAPIProxy();
        proxy.init(router);
        proxy.setSpecs(singletonList(spec));
        proxy.init();
        return proxy;
    }

    @SuppressWarnings("rawtypes")
    public static Map getMapFromResponse(Exchange exc) throws IOException {
        return om.readValue(exc.getResponse().getBody().getContent(), Map.class);
    }

    public static JsonNode getJsonFromResponse(Exchange exc) throws IOException {
        return om.readValue(exc.getResponse().getBody().getContent(), JsonNode.class);
    }

    public static OpenAPIRecord getSingleOpenAPIRecord(Map<String,OpenAPIRecord> m) {
        return (OpenAPIRecord) m.values().toArray()[0];
    }
}
