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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.Test;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;


public class Json2XmlInterceptorTest {

    @Test
    public void validJSONTest() throws Exception {
        assertEquals("sonoo\u00fc\u00f6\u00fc\u00f6", getNameFromDocument(DocumentBuilderFactory.newInstance().newDocumentBuilder().
                parse(processThroughInterceptor(loadResource("/json/convert.json")))));
    }

    @Test
    public void validJSONResponseTest() throws Exception {
        assertEquals("sonoo\u00fc\u00f6\u00fc\u00f6", getNameFromDocument(DocumentBuilderFactory.newInstance().newDocumentBuilder().
                parse(processThroughInterceptorResponse(loadResource("/json/convert.json")))));
    }

    @Test(expected = JSONException.class)
    public void invalidJsonTest() throws Exception {
        getNameFromDocument(DocumentBuilderFactory.newInstance().newDocumentBuilder().
                parse(processThroughInterceptor(new ByteArrayInputStream("invalid json".getBytes(StandardCharsets.UTF_8)))));
    }

    private Request createRequestFromBytes(byte[] bytes) throws IOException {
        return new Request.Builder().contentType(MimeType.APPLICATION_JSON_UTF8).body(bytes).build();
    }

    private Response createResponseFromBytes(byte[] bytes) throws IOException {
        return new Response.ResponseBuilder().contentType(MimeType.APPLICATION_JSON_UTF8).body(bytes).build();
    }

    private InputStream processThroughInterceptor(InputStream stream) throws Exception {
        Exchange exc = fillAndgetExchange(stream);
        new Json2XmlInterceptor().handleRequest(exc);
        return exc.getRequest().getBodyAsStream();
    }

    private InputStream processThroughInterceptorResponse(InputStream stream) throws Exception {
        Exchange exc = fillAndgetExchange(stream);
        new Json2XmlInterceptor().handleResponse(exc);
        return exc.getResponse().getBodyAsStream();
    }

    private Exchange fillAndgetExchange(InputStream stream) throws IOException {
        byte[] res = IOUtils.toByteArray(stream);
        Exchange exc = new Exchange(null);
        exc.setRequest(createRequestFromBytes(res));
        exc.setResponse(createResponseFromBytes(res));
        return exc;
    }

    private InputStream loadResource(String path) {
        return this.getClass().getResourceAsStream(path);
    }

    private String getNameFromDocument(Document document) {
        return document.getElementsByTagName("name").item(0).getFirstChild().getNodeValue();
    }

}
