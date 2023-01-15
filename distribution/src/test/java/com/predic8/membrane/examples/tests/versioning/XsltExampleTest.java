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

import com.predic8.membrane.examples.tests.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.examples.util.BufferLogger;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.*;

import static com.predic8.membrane.test.AssertUtils.*;
import static com.predic8.membrane.test.AssertUtils.postAndAssert;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.readFileToString;

public class XsltExampleTest extends DistributionExtractingTestcase {

    String request_v11;
    String request_v20;

    @BeforeEach
    void setup() throws IOException {
        request_v11 = readFileFromBaseDir("request_v11.xml");
        request_v20 = readFileFromBaseDir("request_v20.xml");
    }

    @Override
    protected String getExampleDirName() {
        return "versioning/xslt";
    }

    @Test
    public void test() throws Exception {
        replaceInFile2("proxies.xml", "8080", "3027");
        replaceInFile2("proxies.xml", "2000", "3028");
        replaceInFile2("src/main/java/com/predic8/contactservice/Launcher.java", "8080", "3027");

        BufferLogger logger = new BufferLogger();
        try(Process2 mvn = new Process2.Builder().in(baseDir).executable("mvn clean compile assembly:single").withWatcher(logger).start()) {
            int exitCode = mvn.waitFor(60000);
            if (exitCode != 0)
                throw new RuntimeException("Maven exited with code " + exitCode + ": " + logger);
        }

        try(Process2 jarNode1 = new Process2.Builder().in(baseDir).waitAfterStartFor("ContactService v20 up.")
                .executable("java -jar ./target/xslt-maven-1.0-SNAPSHOT.jar").start()) {
            sleep(2000);
            assertContains("404", postAndAssert(404, "http://localhost:3027/ContactService/v11", CONTENT_TYPE_TEXT_XML_HEADER, request_v11));

            try(Process2 ignored = startServiceProxyScript()) {
                sleep(1000); // wait for Endpoints to start

                // talk to proxy
                assertContains("Hello John", postAndAssert(200, "http://localhost:3028/ContactService/v20", CONTENT_TYPE_TEXT_XML_HEADER, request_v11));
                assertContains("Hello John", postAndAssert(200, "http://localhost:3028/ContactService/v20", CONTENT_TYPE_TEXT_XML_HEADER, request_v20));

            }
        }
    }
}