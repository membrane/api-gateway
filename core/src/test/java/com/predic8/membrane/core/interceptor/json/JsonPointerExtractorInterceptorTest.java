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

package com.predic8.membrane.core.interceptor.json;


import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.util.List;

public class JsonPointerExtractorInterceptorTest {

    JsonPointerExtractorInterceptor jpe;
    Exchange exc;

    @Before
    public void setUp() throws IOException {
        jpe = new JsonPointerExtractorInterceptor();
        exc = new Exchange(null);
        byte[] resAsByteArray = IOUtils.toByteArray(this.getClass().getResourceAsStream("/json/order.json"));

        exc.setRequest(new Request.Builder().contentType(MimeType.APPLICATION_JSON_UTF8)
                .body(resAsByteArray).build());

        exc.setResponse(new Response.ResponseBuilder().contentType(MimeType.APPLICATION_JSON_UTF8)
                .body(resAsByteArray).build());

    }

    @Test
    public void validTest() throws Exception {
        jpe.getMappings().add(new JsonPointerExtractorInterceptor.
                Property("/orders/0/state", "state"));
        jpe.handleRequest(exc);
        Assert.assertEquals("created", exc.getProperty("state"));

    }


    @Test
    public void validListTest() throws Exception {
        jpe.getMappings().add(new JsonPointerExtractorInterceptor.
                Property("/orders/0/items", "items"));
        jpe.handleRequest(exc);
        Assert.assertEquals("3", ((List)exc.getProperty("items")).get(2));
        Assert.assertEquals("food2", ((List)exc.getProperty("items")).get(1));
    }

    @Test
    public void validResponseTest() throws Exception {
        jpe.getMappings().add(new JsonPointerExtractorInterceptor.
                Property("/orders/0/state", "state"));
        jpe.handleResponse(exc);

        Assert.assertEquals("created", exc.getProperty("state"));
    }


    @Test
    public void nonExistentPathTest() throws Exception {
        jpe.getMappings().add(new JsonPointerExtractorInterceptor.Property("/orders/state/nonexistent", "title" ));
        jpe.handleRequest(exc);

        Assert.assertSame("",exc.getProperty("title"));
    }

    @Test
    public void nameMissingTest() throws Exception {
        JsonPointerExtractorInterceptor.Property prop = new JsonPointerExtractorInterceptor.Property();
        prop.setJsonPointer("/orders/state/nonexistent");
        jpe.getMappings().add(prop);
        jpe.handleRequest(exc);

        Assert.assertSame(null, exc.getProperty("title"));
    }

    @Test
    public void pointerMissingTest() throws Exception {
        JsonPointerExtractorInterceptor.Property prop = new JsonPointerExtractorInterceptor.Property();
        prop.setName("title");
        jpe.getMappings().add(prop);
        jpe.handleRequest(exc);

        Assert.assertTrue(StringUtils.isBlank((String)exc.getProperty("title")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidPointerTest() throws Exception {
        JsonPointerExtractorInterceptor.Property prop = new JsonPointerExtractorInterceptor.Property();
        prop.setJsonPointer("\"/k\"l");
        prop.setName("title");
        jpe.getMappings().add(prop);
        jpe.handleRequest(exc);
    }


}