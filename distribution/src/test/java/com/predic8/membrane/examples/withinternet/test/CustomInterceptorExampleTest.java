/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.*;
import com.predic8.membrane.test.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class CustomInterceptorExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "extending-membrane/custom-interceptor";
    }

    @Test
    public void test() throws Exception {

        mavenPackage();

        try(Process2 sl = startServiceProxyScript(); HttpAssertions ha = new HttpAssertions()) {
            SubstringWaitableConsoleEvent invoked = new SubstringWaitableConsoleEvent(sl, "MyInterceptor maven at request invoked.");
            ha.getAndAssert200(LOCALHOST_2000);
            assertTrue(invoked.occurred());
        }
    }
}
