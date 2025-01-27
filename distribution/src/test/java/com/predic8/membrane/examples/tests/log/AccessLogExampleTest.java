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


package com.predic8.membrane.examples.tests.log;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.WaitableConsoleEvent;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.test.StringAssertions.assertContains;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AccessLogExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "logging/access";
    }

    @Test
    void testConsole() throws Exception {
        try (var process = startServiceProxyScript(); HttpAssertions ha = new HttpAssertions()) {
            var console = new WaitableConsoleEvent(process, p -> p.contains("\"GET / HTTP/1.1\" 200 0 [application/json]"));
            ha.getAndAssert200("http://localhost:2000");
            assertTrue(console.occurred());
        }
    }

    @Test
    void testRollingFile() throws Exception {
        try (var ignore = startServiceProxyScript(); HttpAssertions ha = new HttpAssertions()) {
            ha.getAndAssert200("http://localhost:2000");
        }
        assertContains("\"GET / HTTP/1.1\" 200 0 [application/json]", readFile("access.log"));
    }

    @Test
    void testHeader() throws Exception {
        try (var ignore = startServiceProxyScript(); HttpAssertions ha = new HttpAssertions()) {
            ha.getAndAssert200("http://localhost:2000");
        }
        assertContains("X-Forwarded-For: 127.0.0.1", readFile("access.log"));
    }
}
