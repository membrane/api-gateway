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
package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN_UTF8;
import static com.predic8.membrane.test.AssertUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class InternalProxyTest extends DistributionExtractingTestcase {

    final static String ENDPOINT_URL = "http://localhost:2000/axis2/services/BLZService";

    @Override
    protected String getExampleDirName() {
        return "internalproxy";
    }

    @Test
    public void testWsdl() throws Exception {
        try(Process2 sl = startServiceProxyScript()) {
            assertContains("Service Proxy: BLZService", getAndAssert200(ENDPOINT_URL));
        }
    }

    @Test
    public void testSoapRequest() throws Exception {
        try(Process2 ignored = startServiceProxyScript()) {
            assertContains("COLSDE33XXX", postAndAssert(200,ENDPOINT_URL, getSoapRequestHeader(), readFileFromBaseDir("soap_request.xml")));
        }
    }

    private String[] getSoapRequestHeader() {
        return new String[]{"Content-Type", TEXT_PLAIN_UTF8, "SOAPAction", "Get"};
    }

    @Test
    public void testCbrRequest() throws Exception {
        replaceInFile2("service-proxy.sh","proxies_soap", "proxies_service");

        try(Process2 sl = startServiceProxyScript()) {
            SubstringWaitableConsoleEvent internalOutput =
                    new SubstringWaitableConsoleEvent(sl, "Inside proxy mybackend");
            postAndAssert(200,"http://localhost:2000", new String[]{"Content-Type", "text/xml;charset=UTF-8"}, readFileFromBaseDir("express.xml"));
            assertTrue(internalOutput.occurred());
        }
    }

    @Test
    public void testCbrRequestWithoutInternal() throws Exception {
        replaceInFile2("service-proxy.sh","proxies_soap", "proxies_service");

        try(Process2 sl = startServiceProxyScript()) {
            SubstringWaitableConsoleEvent internalOutput =
                    new SubstringWaitableConsoleEvent(sl, "Inside proxy mybackend");
            getAndAssert200("http://localhost:2000");
            assertFalse(internalOutput.occurred());
        }
    }
}
