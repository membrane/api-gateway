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

package com.predic8.membrane.examples.config;

import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.net.http.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.net.http.HttpResponse.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProxiesXMLSoapTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    private Process2 process;

    @BeforeEach
    void startMembrane() throws Exception {
        process = new Process2.Builder().in(baseDir).script("service-proxy").parameters("-c conf/proxies-soap.xml").waitForMembrane().start();
    }

    @AfterEach
    void stopMembrane() throws IOException, InterruptedException {
        process.killScript();
    }

    @Test
    void getWebServicesExplorer2000() throws Exception {
        testWebServiceExplorer(URL_2000 + "/blz-service");
    }

    @Test
    void getWebServicesExplorer2001() throws Exception {
        testWebServiceExplorer("http://localhost:2001/blz-service");
    }

    private void testWebServiceExplorer(String url) throws Exception {
        HttpResponse<String> response = getHttpClient().send(HttpRequest.newBuilder().uri(new URI(url)).build(), BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("BLZService"));
    }

    @Test
    void getWSDL2000() throws Exception {
        testGetWSDL(URL_2000 + "/blz-service?wsdl");
    }

    @Test
    void getWSDL2001() throws Exception {
        testGetWSDL("http://localhost:2001/blz-service?wsdl");
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private void testGetWSDL(String url) throws Exception {
        HttpResponse<String> response = getHttpClient().send(HttpRequest.newBuilder().uri(new URI(url)).build(), BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(TEXT_XML, response.headers().firstValue("Content-Type").get());
        assertTrue(response.body().contains("BLZService"));
        assertTrue(response.body().contains("binding"));
    }


    private HttpClient getHttpClient()  {
        return HttpClient.newBuilder().build();
    }
}
