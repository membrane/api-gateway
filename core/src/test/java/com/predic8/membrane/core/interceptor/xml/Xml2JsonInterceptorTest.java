/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xml;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;


public class Xml2JsonInterceptorTest {

    @Test
    public void invalidXml() throws Exception {
        Assert.assertEquals("{}", getJsonRootFromStream(processThroughInterceptor(new ByteArrayInputStream("5".getBytes(StandardCharsets.UTF_8))))
                        .toString());
    }

    @Test
    public void validTestxml2json() throws Exception {
        //\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6 = üöüöüö
        assertEquals("\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6",
                ((JsonNode) getJsonRootFromStream(processThroughInterceptor(loadResource("/xml/convert.xml"))).get("bar")).get("futf").asText());
    }


    @Test
    public void validTestxml2jsonResponse() throws Exception {
        //\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6 = üöüöüö
        assertEquals("\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6",
                ((JsonNode) getJsonRootFromStream(processThroughInterceptorResponse(loadResource("/xml/convert.xml"))).get("bar")).get("futf").asText());
    }

    private JsonNode getJsonRootFromStream(InputStream stream) throws IOException {
        return new ObjectMapper().readTree(stream);
    }

    private Request createRequestFromBytes(byte[] bytes) throws IOException {
        return new Request.Builder().contentType(MimeType.TEXT_XML).body(bytes).build();
    }

    private Response createResponseFromBytes(byte[] bytes) throws IOException {
        return new Response.ResponseBuilder().contentType(MimeType.TEXT_XML).body(bytes).build();
    }

    private InputStream processThroughInterceptor(InputStream stream) throws Exception {
        Exchange exc = fillAndGetExchange(stream);
        new Xml2JsonInterceptor().handleRequest(exc);
        return exc.getRequest().getBodyAsStream();
    }

    private InputStream processThroughInterceptorResponse(InputStream stream) throws Exception {
        Exchange exc = fillAndGetExchange(stream);
        new Xml2JsonInterceptor().handleResponse(exc);
        return exc.getResponse().getBodyAsStream();
    }

    private InputStream loadResource(String path) {
        return this.getClass().getResourceAsStream(path);
    }

    private Exchange fillAndGetExchange(InputStream stream) throws IOException {
        Exchange exc = new Exchange(null);
        byte[] bytes = IOUtils.toByteArray(stream);
        exc.setRequest(createRequestFromBytes(bytes));
        exc.setResponse(createResponseFromBytes(bytes));
        return exc;

    }

}
