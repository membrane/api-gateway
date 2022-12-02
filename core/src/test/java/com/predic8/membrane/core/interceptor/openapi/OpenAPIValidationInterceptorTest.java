package com.predic8.membrane.core.interceptor.openapi;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.util.*;
import org.junit.*;
import org.springframework.http.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_UTF8;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.util.Utils.inputStreamToString;
import static org.junit.Assert.*;

public class OpenAPIValidationInterceptorTest {

    private static ObjectMapper om = new ObjectMapper();

    Exchange e1;
    OpenAPIValidationInterceptor interceptor;

    @Before
    public void setUp() {
        interceptor = new OpenAPIValidationInterceptor();
        interceptor.setValidateResponse(true);
        interceptor.validator = new OpenAPIValidator(getResourceAsStream("/openapi/customers.yml"));
    }

    @Test
    public void handleRequestValidateRequest() throws Exception {
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("GET").contentType(APPLICATION_JSON_UTF8)
                .build());

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    @Test
    public void handleRequestValidateRequestInvalidGET() throws Exception {
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers?limit=1000");
        exc.setRequest(new Request.Builder().method("GET").build());
        assertEquals(RETURN, interceptor.handleRequest(exc));
        JsonNode node = om.readTree(exc.getResponse().getBody().getContent());
        System.out.println("node.toPrettyString() = " + node.toPrettyString());
        assertEquals(1, node.size());
    }

    @Test
    public void handleRequestValidateRequestInvalidPOST() throws Exception {
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("POST").contentType(APPLICATION_JSON_UTF8)
                .body( getResourceAsString("/openapi/invalid-customer.json")).build());

        assertEquals(RETURN, interceptor.handleRequest(exc));
        JsonNode node = om.readTree(exc.getResponse().getBody().getContent());
//        System.out.println("node.toPrettyString() = " + node.toPrettyString());
        assertEquals(3, node.get("validationErrors").size());
    }

    @Test
    public void handleResponseInvalidResponse() throws Exception {
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers/7");
        exc.setRequest(new Request.Builder().method("GET").build());

        exc.setResponse(Response.ok().contentType(APPLICATION_JSON_UTF8)
                .body( getResourceAsString("/openapi/invalid-customer.json")).build());

        //        System.out.println("node.toPrettyString() = " + node.toPrettyString());

        assertEquals(RETURN, interceptor.handleResponse(exc));
        JsonNode node = om.readTree(exc.getResponse().getBody().getContent());

        assertEquals(3, node.get("validationErrors").size());
    }

    @Test
    public void matchBasePath() throws Exception {

        OpenAPIValidationInterceptor interceptor = new OpenAPIValidationInterceptor();
        interceptor.validator = new OpenAPIValidator(getResourceAsStream("/openapi/info-servers.yml"));
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/base/v2/foo");
        exc.setRequest(new Request.Builder().method("GET").build());

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    @Test
    public void serverWithoutTrailingSlash() throws Exception {

        OpenAPIValidationInterceptor interceptor = new OpenAPIValidationInterceptor();
        interceptor.validator = new OpenAPIValidator(getResourceAsStream("/openapi/servers-without-slash.yml"));
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/foo");
        exc.setRequest(new Request.Builder().method("GET").build());

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    private String getResourceAsString(String filename) throws Exception {



        return inputStreamToString(getResourceAsStream(filename));
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }
}