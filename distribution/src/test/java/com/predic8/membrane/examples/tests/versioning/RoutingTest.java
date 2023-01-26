/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.postAndAssert;
import static java.lang.Thread.sleep;

import java.io.IOException;

import com.predic8.membrane.examples.util.BufferLogger;
import org.junit.jupiter.api.*;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;

public class RoutingTest extends DistributionExtractingTestcase {

    String request_v11;
    String request_v20;

    @BeforeEach
    void setup() throws IOException {
        request_v11 = readFileFromBaseDir("request_v11.xml");
        request_v20 = readFileFromBaseDir("request_v20.xml");
    }

    @Override
    protected String getExampleDirName() {
        return "versioning/routing";
    }

    @Test
    public void test() throws Exception {
        replaceInFile2("proxies.xml", "8080", "3024");
        replaceInFile2("proxies.xml", "2000", "3025");
        replaceInFile2("src/main/java/com/predic8/contactservice/Launcher.java", "8080", "3024");

        BufferLogger b = new BufferLogger();
        try(Process2 mvn = new Process2.Builder().in(baseDir).executable("mvn clean compile assembly:single").withWatcher(b).start()) {
            if (mvn.waitFor(60000) != 0)
                throw new RuntimeException("Maven exited with code " + mvn.waitFor(60000) + ": " + b);
        }

        try(Process2 ignored1 = startServiceProxyScript()) {
            try( Process2 ignored2 = new Process2.Builder().in(baseDir).waitAfterStartFor("ContactService v11 and v20 up.")
                    .executable("java -jar ./target/routing-maven-1.0-SNAPSHOT.jar").start()) {
                sleep(2000); // wait for Endpoints to start

                // directly talk to versioned endpoints
                assertContains("1.1", postAndAssert(200, "http://localhost:3024/ContactService/v11", CONTENT_TYPE_TEXT_XML_HEADER, request_v11));
                assertContains("2.0", postAndAssert(200, "http://localhost:3024/ContactService/v20", CONTENT_TYPE_TEXT_XML_HEADER, request_v20));

                // talk to wrong endpoint
                postAndAssert(500, "http://localhost:3024/ContactService/v20", CONTENT_TYPE_TEXT_XML_HEADER, request_v11);

                // talk to proxy
                assertContains("1.1", postAndAssert(200, "http://localhost:3025/ContactService", CONTENT_TYPE_TEXT_XML_HEADER, request_v11));
                assertContains("2.0", postAndAssert(200, "http://localhost:3025/ContactService", CONTENT_TYPE_TEXT_XML_HEADER, request_v20));
            }
        }
    }
}
