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

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import com.predic8.membrane.test.AssertUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static com.predic8.membrane.test.AssertUtils.postAndAssert;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InternalProxyTest extends DistributionExtractingTestcase {


    @Test
    public void testWsdl() throws IOException, InterruptedException {
        File baseDir = getExampleDir("internalproxy");
        Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();
        try {
            String result = getAndAssert200("http://localhost:2000/axis2/services/BLZService");
            AssertUtils.assertContains("Service Proxy: BLZService", result);
        } finally {
            sl.killScript();
        }
    }

    @Test
    public void testSoapRequest() throws IOException, InterruptedException {
        File baseDir = getExampleDir("internalproxy");
        Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();

        try {
            String body = new String(Files.readAllBytes(Paths.get(baseDir + FileSystems.getDefault().getSeparator()
                    + "soap_request.xml")));
            String[] headers = {"Content-Type", "text/xml;charset=UTF-8", "SOAPAction", "Get"};
            String response = postAndAssert(200,"http://localhost:2000/axis2/services/BLZService", headers, body);
            AssertUtils.assertContains("COLSDE33XXX", response);
        } finally {
            sl.killScript();
        }
    }

    @Test
    public void testCbrRequest() throws IOException, InterruptedException {
        File baseDir = getExampleDir("internalproxy");
        AssertUtils.replaceInFile(new File(baseDir, "service-proxy.sh"), "proxies_soap", "proxies_service");
        Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();

        try {
            SubstringWaitableConsoleEvent internalOutput =
                    new SubstringWaitableConsoleEvent(sl, "Inside proxy mybackend");
            String body = new String(Files.readAllBytes(Paths.get(baseDir + FileSystems.getDefault().getSeparator()
                    + "express.xml")));
            String[] headers = {"Content-Type", "text/xml;charset=UTF-8"};
            postAndAssert(200,"http://localhost:2000", headers, body);
            assertTrue(internalOutput.occurred());


        } finally {
            sl.killScript();
        }
    }

    @Test
    public void testCbrRequestWithoutInternal() throws IOException, InterruptedException {
        File baseDir = getExampleDir("internalproxy");
        AssertUtils.replaceInFile(new File(baseDir, "service-proxy.sh"), "proxies_soap", "proxies_service");
        Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();

        try {
            SubstringWaitableConsoleEvent internalOutput =
                    new SubstringWaitableConsoleEvent(sl, "Inside proxy mybackend");
            getAndAssert200("http://localhost:2000");
            assertFalse(internalOutput.occurred());

        } finally {
            sl.killScript();
        }
    }

}
