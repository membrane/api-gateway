/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.predic8.membrane.core.interceptor.beautifier;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class BeautifierInterceptorTest {
    ObjectMapper om = new ObjectMapper();

    BeautifierInterceptor beautifierInterceptor;

    Exchange jsonExchange;
    Exchange xmlExchange;

    Response response;

    Request req;


    JsonNode testJson = om.readTree("{\"test\": \"foo\", \"sad\": \"sad\"}");
    byte[] testXml = ("<foo><bar>baz</bar></foo>").getBytes(UTF_8);

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
        req.setBodyContent(testXml);
        xmlExchange.setRequest(req);
        assertFalse(xmlExchange.getRequest().getBody().toString().contains("\n"));
        beautifierInterceptor.handleRequest(xmlExchange);
        assertTrue(xmlExchange.getRequest().getBody().toString().contains("\n"));
    }
}
