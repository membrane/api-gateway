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

package com.predic8.membrane.examples.tests.openapi;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.test.AssertUtils.*;
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenAPIProxyTest extends DistributionExtractingTestcase {

    final ObjectMapper om = new ObjectMapper();

    final String[] ACCEPT_HTML_HEADER = {"Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"};

    @Override
    protected String getExampleDirName() {
        return "openapi/openapi-proxy";
    }

    private Process2 process;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        process = startServiceProxyScript();
        sleep(100);
    }

    @AfterEach
    void stopMembrane() throws IOException, InterruptedException {
        process.killScript();
    }

    @SuppressWarnings("JsonSchemaCompliance")
    @Test
    void api_doc() throws IOException {
        String andAssert = getAndAssert(200, LOCALHOST_2000 + "/api-doc");
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
                """, andAssert,true);
    }

    @Test
    void apiOverview() throws IOException {
        String body = getAndAssert(200, LOCALHOST_2000 + "/api-doc", ACCEPT_HTML_HEADER);
        assertContains("""
                <h1 class="title">APIs</h1>
                """, body);
        assertContains("Fruit Shop API", body);
        assertContains("/shop", body);
    }

    @Test
    void swaggerUi() throws IOException {
        String body = getAndAssert(200, LOCALHOST_2000 + "/api-doc/ui/fruitshop-v1-0", ACCEPT_HTML_HEADER);
        assertContains("""
                content="SwaggerUI""", body);
        assertContains("/api-doc/fruitshop-v1-0", body);
    }

    @SuppressWarnings("unchecked")
    @Test
    void postLikeSwaggerUI() throws Exception {

        Map<String,Object> json = om.readValue(postAndAssert(201, LOCALHOST_2000 + "/shop/products/", CONTENT_TYPE_APP_JSON_HEADER, """
                {
                     "name": "Figs",
                     "price": 2.7
                 }
                """), Map.class);
        assertEquals("Figs", json.get("name"));
        assertEquals(2.7, json.get("price"));
    }

    @Test
    void postLikeSwaggerUIInvalidPrice() throws Exception {
        JSONAssert.assertEquals("""
                {
                  "method" : "POST",
                  "uriTemplate" : "/products/",
                  "path" : "/shop/products/",
                  "validationErrors" : {
                    "REQUEST/BODY#/price" : [ {
                      "message" : "-2.7 is smaller than the minimum of 0",
                      "complexType" : "Product",
                      "schemaType" : "number"
                    } ]
                  }
                }
                """
                , postAndAssert(400, LOCALHOST_2000 + "/shop/products/", CONTENT_TYPE_APP_JSON_HEADER, """
                        {
                             "name": "Figs",
                             "price": -2.7
                         }
                """),true);
    }
}
