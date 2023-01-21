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

package com.predic8.membrane.examples.tutorials.rest;

import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.util.FileUtil.*;
import static com.predic8.membrane.test.AssertUtils.*;

/**
 * See: https://membrane-api.io/tutorials/rest/
 *
 * Needs an Internet connection to work!
 */
public class TurorialRestStepsTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "../tutorials/rest";
	}

	@BeforeEach
	void setup() throws IOException {
		InputStream stepsProxiesAsStream = getStepsProxiesAsStream();
		System.out.println(stepsProxiesAsStream);
		writeInputStreamToFile(baseDir + "/proxies.xml", stepsProxiesAsStream);
	}

	@Test
	public void start() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
			assertContains("Shop API", getAndAssert200(URL_2000));
			assertContains("Membrane Service Proxy Administration", getAndAssert200("http://localhost:9000/admin/"));
		}
	}

	@Test
	public void step1() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
			assertContains("products_url", getAndAssert200("http://localhost:2001/shop/"));
			getAndAssert(400, "http://localhost:2001");
		}
	}

	@Test
	public void step2() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
			assertContains("products_url", getAndAssert200("http://localhost:2002/shop/"));
			assertContains("country", getAndAssert200("http://thomas-bayer.com/restnames/namesincountry.groovy?country=Germany"));
		}
	}

	private InputStream getStepsProxiesAsStream() {
		return getClass().getClassLoader().getResourceAsStream("com/predic8/membrane/examples/tutorials/rest/rest-tutorial-steps-proxies.xml");
	}
}
