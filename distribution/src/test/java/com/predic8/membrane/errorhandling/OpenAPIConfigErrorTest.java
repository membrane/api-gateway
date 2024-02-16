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

package com.predic8.membrane.errorhandling;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.FileUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAPIConfigErrorTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    @Test
    void wrongFileLocation() throws Exception {
        BufferLogger logger = new BufferLogger();
        writeInputStreamToFile(baseDir + "/conf/proxies.xml", getResourceAsStream("com/predic8/membrane/errorhandling/wrong-file-location-proxies.xml"));
        try(Process2 ignored = new Process2.Builder().in(baseDir).script("service-proxy").withWatcher(logger).waitAfterStartFor("giving up").start()) {
            assertTrue(logger.contains("Cannot read"));
            assertTrue(logger.contains(": abc"));
        }
    }

    @Test
    void wrongURLLocation() throws Exception {
        BufferLogger logger = new BufferLogger();
        writeInputStreamToFile(baseDir + "/conf/proxies.xml", getResourceAsStream("com/predic8/membrane/errorhandling/wrong-url-location-proxies.xml"));
        try(Process2 ignored = new Process2.Builder().in(baseDir).script("service-proxy").withWatcher(logger).waitAfterStartFor("giving up").start()) {
//            System.out.println("logger = " + logger);
            assertTrue(logger.contains("Error accessing OpenAPI"));
            assertTrue(logger.contains(": http://abc"));
        }
    }

    @Test
    void wrongContent() throws Exception {
        BufferLogger logger = new BufferLogger();
        writeInputStreamToFile(baseDir + "/conf/proxies.xml", getResourceAsStream("com/predic8/membrane/errorhandling/wrong-content-proxies.xml"));
        try(Process2 ignored = new Process2.Builder().in(baseDir).script("service-proxy").withWatcher(logger).waitAfterStartFor("giving up").start()) {
            assertTrue(logger.contains("Cannot read"));
            assertTrue(logger.contains("client.cer"));
        }
    }
}