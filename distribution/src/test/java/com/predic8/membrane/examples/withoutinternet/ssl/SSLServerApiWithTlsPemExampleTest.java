/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples.withoutinternet.ssl;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.test.StringAssertions.assertContains;

public class SSLServerApiWithTlsPemExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "security/ssl-tls/api-with-tls-pem";
    }

    @Test
    void test() throws Exception {
        replaceInFile2("proxies.xml", "443", "3023");

        try(Process2 ignored = startServiceProxyScript(); HttpAssertions ha = new HttpAssertions()) {
            ha.trustAnyHTTPSServer(3023);
            assertContains("success", ha.getAndAssert200("https://localhost:3023"));
        }
    }

}
