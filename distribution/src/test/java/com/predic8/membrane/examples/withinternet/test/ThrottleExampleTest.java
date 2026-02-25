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

package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThrottleExampleTest extends AbstractSampleMembraneStartStopTestcase {

	@Override
	protected String getExampleDirName() {
		return "routing-traffic/throttle";
	}

	@Test
	public void test() throws Exception {
		when().get(LOCALHOST_2000).then().statusCode(200);
		long start = System.currentTimeMillis();
		when().get(LOCALHOST_2000).then().statusCode(200);
        assertTrue(System.currentTimeMillis() - start >= 1000);
	}
}
