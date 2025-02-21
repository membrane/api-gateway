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

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.test.StringAssertions.assertContains;
import static com.predic8.membrane.test.StringAssertions.assertContainsNot;

public class XSLTExampleTest extends AbstractSampleMembraneStartStopTestcase {

    public static final String PATH = "/restnames/name.groovy?name=Pia";
    public static final String CUSTOMER_HOST_LOCAL = "http://localhost:2000";
    public static final String CUSTOMER_HOST_REMOTE = "https://api.predic8.de";

    @Override
    protected String getExampleDirName() {
        return "xml/xslt";
    }

    @Test
    public void test() throws Exception {
        try (HttpAssertions ha = new HttpAssertions()) {
            assertContains("<male>", ha.getAndAssert200(CUSTOMER_HOST_REMOTE + PATH));
            assertContainsNot("<male>", ha.getAndAssert200(CUSTOMER_HOST_LOCAL + PATH));
        }
    }
}
