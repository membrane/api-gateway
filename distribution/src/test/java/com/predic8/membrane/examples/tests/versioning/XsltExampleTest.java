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
package com.predic8.membrane.examples.tests.versioning;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.BufferLogger;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.predic8.membrane.test.AssertUtils.*;
import static com.predic8.membrane.test.AssertUtils.postAndAssert;

public class XsltExampleTest extends DistributionExtractingTestcase {

    @Test
    public void test() throws IOException, InterruptedException {
        File base = getExampleDir("versioning/xslt-maven");

        String header[] = new String[] { "Content-Type", "text/xml" };
        String request_v11 = FileUtils.readFileToString(new File(base, "request_v11.xml"));
        String request_v20 = FileUtils.readFileToString(new File(base, "request_v20.xml"));

        replaceInFile(new File(base, "proxies.xml"), "8080", "3027");
        replaceInFile(new File(base, "proxies.xml"), "2000", "3028");
        replaceInFile(new File(base, "src/main/java/com/predic8/contactservice/Launcher.java"), "8080", "3027");

        BufferLogger b = new BufferLogger();
        Process2 mvn = new Process2.Builder().in(base).executable("mvn clean compile assembly:single").withWatcher(b).start();
        try {
            int exitCode = mvn.waitFor(60000);
            if (exitCode != 0)
                throw new RuntimeException("Maven exited with code " + exitCode + ": " + b.toString());
        } finally {
            mvn.killScript();
        }


        Process2 jarNode1 = new Process2.Builder().in(base).waitAfterStartFor("ContactService v20 up.")
                .executable("java -jar ./target/xslt-maven-1.0-SNAPSHOT.jar").start();
        try {
            Thread.sleep(2000);
            assertContains("404", postAndAssert(404, "http://localhost:3027/ContactService/v11", header, request_v11));

            Process2 sl = new Process2.Builder().in(base).script("service-proxy").waitForMembrane().start();

            try {
                Thread.sleep(2000); // wait for Endpoints to start

                // talk to proxy
                assertContains("Hello John", postAndAssert(200, "http://localhost:3028/ContactService/v20", header, request_v11));
                assertContains("Hello John", postAndAssert(200, "http://localhost:3028/ContactService/v20", header, request_v20));

            } finally {
                sl.killScript();
            }

        } finally {
            jarNode1.killScript();

        }

    }
}
