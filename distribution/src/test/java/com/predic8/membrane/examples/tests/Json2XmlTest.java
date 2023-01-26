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
package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.test.AssertUtils.postAndAssert;
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Json2XmlTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "json-2-xml";
    }

    @Test
    public void test() throws Exception {
        BufferLogger logger = new BufferLogger();
        try(Process2 ignored = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().withWatcher(logger).start()) {
            sleep(1000);
            postAndAssert(200,"http://localhost:2000/", new String[]{"Content-Type", APPLICATION_JSON}, readFileFromBaseDir("customers.json"));
            sleep(500);
            assertTrue(logger.toString().contains("<count>269</count>"));
        }
    }
}