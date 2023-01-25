/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.config;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.util.*;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.util.*;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.test.AssertUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProxiesXMLTest extends DistributionExtractingTestcase {

    final ObjectMapper om = new ObjectMapper();

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    private Process2 process;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        process = startServiceProxyScript();
    }

    @AfterEach
    void stopMembrane() throws IOException, InterruptedException {
        process.killScript();
    }

    @Test
    void api_doc() throws IOException {
        String andAssert = getAndAssert(200, URL_2000 + "/api-doc");
        System.out.println(andAssert);
        JSONAssert.assertEquals("""
                {
                  "fruitshop-v1-0" : {
                    "openapi" : "3.0.2",
                    "title" : "Fruit Shop API",
                    "version" : "1.0",
                    "openapi_link" : "/api-doc/fruitshop-v1-0",
                    "ui_link" : "/api-doc/ui/fruitshop-v1-0"
                  }
                }
                """, andAssert, true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void postLikeSwaggerUI() throws Exception {

        Map<String, Object> json = om.readValue(postAndAssert(201, URL_2000 + "/shop/products/", CONTENT_TYPE_APP_JSON_HEADER, """
                {
                     "name": "Figs",
                     "price": 2.7
                 }
                """), Map.class);
        assertEquals("Figs", json.get("name"));
        assertEquals(2.7, json.get("price"));
    }

    @Test
    public void names() throws Exception {
        HttpResponse res = getAndAssertWithResponse(200, URL_2000 + "/names/Pia", null);
        assertContains("json", getContentTypeValue(res));
        assertContains("Pia", EntityUtils.toString(res.getEntity()));

        getAndAssert(200, URL_2000 + "/names/Pia", null);
        getAndAssert(200, URL_2000 + "/names/Pia", null);
        getAndAssert(429, URL_2000 + "/names/Pia", null);
    }

    @Test
    public void groovy() throws Exception {
        AtomicBoolean headingFound = new AtomicBoolean();
        AtomicBoolean hostFound = new AtomicBoolean();
        process.addConsoleWatcher((error, line) -> {
            if (line.contains("Request headers"))
                headingFound.set(true);
            if (line.contains("Host: localhost:2000"))
                hostFound.set(true);
        });
        HttpResponse res = getAndAssertWithResponse(200, URL_2000 + "/header", null);
        assertContains("json", getContentTypeValue(res));
        assertContains("ok", EntityUtils.toString(res.getEntity()));

//        Thread.sleep(500);
        assertTrue(headingFound.get());
        assertTrue(hostFound.get());

    }

    @Test
    public void normalAPI() throws Exception {
        // Low level to get the Entity and to close the request
        HttpGet get = new HttpGet(URL_2000);
        try {
            HttpResponse r = invokeAndAssertInternal(200, URL_2000, null, get);
            HttpEntity e = r.getEntity();
            assertContains("shop", EntityUtils.toString(e));
            assertContains("json", e.getContentType().getValue());
        } finally {
            get.releaseConnection();
        }
    }

    private String getContentTypeValue(HttpResponse res) {
        return res.getEntity().getContentType().getValue();
    }
}
