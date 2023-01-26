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

package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.test.AssertUtils.*;
import static java.io.File.*;
import static java.lang.Thread.*;
import static org.junit.jupiter.api.Assertions.*;

public class AddSoapHeaderTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "soap" + separator + "add-soap-header";
    }

    @Test
    public void test() throws Exception {
        BufferLogger logger = new BufferLogger();

        try(Process2 mvn = new Process2.Builder().in(baseDir).executable("mvn package").withWatcher(logger).start()) {
            if (mvn.waitFor(60000) != 0)
                throw new RuntimeException("Maven exited with code " + mvn.waitFor(60000) + ": " + logger);
        }

        BufferLogger proxyWatcher = new BufferLogger();
        try(Process2 ignored = startServiceProxyScript(proxyWatcher)) {
            postAndAssert(200, LOCALHOST_2000, CONTENT_TYPE_APP_XML_HEADER, readFileFromBaseDir("soap-message-without-header.xml"));
            sleep(500);
            assertTrue(proxyWatcher.contains("wss:Security"));
        }
    }
}