package com.predic8.membrane.core.interceptor.beautifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class BeautifierInterceptorTest {
    ObjectMapper om = new ObjectMapper();

    BeautifierInterceptor beautifierInterceptor;

    Exchange jsonExchange;
    Exchange xmlExchange;

    Response response;

    Request req;


    JsonNode testJson = om.readTree("{\"test\": \"foo\", \"sad\": \"sad\"}");
    String testXml = "<test><foo>asd</foo></test>";

    public BeautifierInterceptorTest() throws JsonProcessingException {}

    @BeforeEach
    void setUp() throws URISyntaxException {
        beautifierInterceptor = new BeautifierInterceptor();
        jsonExchange = Request.post("/foo").contentType(APPLICATION_JSON).buildExchange();
        xmlExchange = Request.post("/foo").contentType(APPLICATION_XML).buildExchange();
        response = Response.ok().contentType(TEXT_PLAIN).body("Message").build();
    }

    @Test
    void JSONBeautifierTest () throws Exception {
        req = jsonExchange.getRequest();
        req.setBodyContent(om.writeValueAsBytes(testJson));
        jsonExchange.setRequest(req);
        assertFalse(jsonExchange.getRequest().getBody().toString().contains("\n"));
        beautifierInterceptor.handleRequest(jsonExchange);
        assertTrue(jsonExchange.getRequest().getBody().toString().contains("\n"));
    }

    @Test
    void XMLBeautifierTest() throws Exception {
        req = xmlExchange.getRequest();
        req.setBodyContent(om.writeValueAsBytes(testXml));
        xmlExchange.setRequest(req);
        System.out.println(xmlExchange.getRequest().getBody().toString());
        beautifierInterceptor.handleRequest(xmlExchange);
        System.out.println(xmlExchange.getRequest().getBody().toString());
    }

}
