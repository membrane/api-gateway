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

package com.predic8.membrane.examples.withinternet.rest;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.test.StringAssertions.assertContains;

/**
 * See: <a href="https://membrane-api.io/tutorials/rest/">REST tutorials</a>
 * <p>
 * Needs an Internet connection to work!
 */
public class TutorialRestInitialExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "../tutorials/rest";
    }

    @Test
    public void testStart() throws Exception {
        try (HttpAssertions ha = new HttpAssertions()) {
            assertContains("Shop API", ha.getAndAssert200(LOCALHOST_2000));
            assertContains("Membrane API Gateway Administration", ha.getAndAssert200("http://localhost:9000/admin/"));
        }
    }
}
