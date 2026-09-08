/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class ReturnInterceptorTest {

    ReturnInterceptor interceptor;

    Exchange post;
    Exchange get;

    Response response;

    @BeforeEach
    void setUp() throws URISyntaxException {
        interceptor = new ReturnInterceptor();
        post = Request.post("/shop").contentType(APPLICATION_JSON).buildExchange();
        get = Request.get("/foo").buildExchange();
        response = Response.ok().contentType(TEXT_PLAIN).body("Message").build();
    }

    @Test
    void getRequestNoResponse() throws Exception {
        interceptor.handleRequest(post);
        assertEquals(200, post.getResponse().getStatusCode());
        assertEquals(APPLICATION_JSON, post.getResponse().getHeader().getContentType());
    }

    @Test
    void getRequestResponse() throws Exception {
        post.setResponse(response);
        interceptor.handleRequest(post);
        assertEquals(200, post.getResponse().getStatusCode());
        assertEquals(TEXT_PLAIN, post.getResponse().getHeader().getContentType());
        assertEquals("Message", post.getResponse().getBodyAsStringDecoded());
    }

    @Test
    void overwrite() throws Exception {
        post.setResponse(response);
        interceptor.setContentType(TEXT_XML);
        interceptor.setStatusCode(415);
        interceptor.handleRequest(post);
        assertEquals(415, post.getResponse().getStatusCode());
        assertEquals(TEXT_XML, post.getResponse().getHeader().getContentType());
    }

    @Test
    void getRequestNoContentToReturn() throws Exception {
        interceptor.handleRequest(get);
        assertNull(get.getResponse().getHeader().getContentType());
        assertEquals(0,get.getResponse().getHeader().getContentLength());
    }

    /**
     * An interceptor upstream of {@code return} (e.g. wsSecurity) may have replaced the request's
     * body with an {@link XmlDomBody} to reflect a DOM mutation - not a plain {@link Body}, but
     * still an {@link AbstractBody}. That body must come through unchanged, not be dropped.
     */
    @Test
    void requestWithXmlDomBodyIsReturned() throws Exception {
        Exchange xml = Request.post("/shop").contentType(TEXT_XML).body("<a><b/></a>").buildExchange();
        XmlDomBody.modify(xml.getRequest(), doc -> doc.getDocumentElement().appendChild(doc.createElement("c")));

        interceptor.handleRequest(xml);

        String body = xml.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("<c/>"), "Expected the DOM-mutated body, got: " + body);
        assertEquals(body.getBytes(StandardCharsets.UTF_8).length, xml.getResponse().getHeader().getContentLength());
    }
}