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

import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.util.*;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.util.*;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.*;

import java.io.*;

import static com.predic8.membrane.test.AssertUtils.*;

public class ProxiesXMLOfflineTest extends DistributionExtractingTestcase {

    static final String URL_3000 = "http://localhost:3000";

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    private Process2 process;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        process =  new Process2.Builder().in(baseDir).script("service-proxy").parameters("-c conf/proxies-offline.xml").waitForMembrane().start();
    }

    @AfterEach
    void stopMembrane() throws IOException, InterruptedException {
        process.killScript();
    }

    @SuppressWarnings("JsonSchemaCompliance")
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

    @Test
    public void port2000() throws Exception {
        runTestOnURL(URL_2000);
    }

    @Test
    public void port3000() throws Exception {
        runTestOnURL(URL_3000);
    }

    private void runTestOnURL(String url) throws IOException {
        // Low level to get the Entity and to close the request
        HttpGet get = new HttpGet(url);
        try {
            HttpResponse r = invokeAndAssertInternal(200, url, null, get);
            HttpEntity e = r.getEntity();
            assertContains("json", e.getContentType().getValue());
            assertContains("success", EntityUtils.toString(e));
        } finally {
            get.releaseConnection();
        }
    }
}
