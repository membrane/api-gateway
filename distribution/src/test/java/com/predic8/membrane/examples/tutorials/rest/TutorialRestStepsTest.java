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
import org.apache.http.*;
import org.apache.http.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.util.FileUtil.*;
import static com.predic8.membrane.test.AssertUtils.*;

/**
 * See: https://membrane-api.io/tutorials/rest/
 *
 * Needs an Internet connection to work!
 */
public class TutorialRestStepsTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "../tutorials/rest";
	}

	@BeforeEach
	void setup() throws IOException {
		writeInputStreamToFile(baseDir + "/proxies.xml", getStepsProxiesAsStream());
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

			HttpResponse res = getAndAssertWithResponse(200,"http://localhost:2002/restnames/name.groovy?name=Pia",null);
			assertContains("xml", getContentTypeValue(res));
			assertContains("nameinfo",EntityUtils.toString(res.getEntity()));
		}
	}

	@Test
	public void step3() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
			HttpResponse res = getAndAssertWithResponse(200,"http://localhost:2003/restnames/name.groovy?name=Pia",null);
			assertContains("json", getContentTypeValue(res));
			assertContains("nameinfo",EntityUtils.toString(res.getEntity()));
		}
	}

	/**
	 * Same as Step 3 but with beautifier and ratelimiter
	 */
	@Test
	public void step4() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
			HttpResponse res = getAndAssertWithResponse(200,"http://localhost:2004/restnames/name.groovy?name=Pia",null);
			assertContains("json", getContentTypeValue(res));
			assertContains("nameinfo",EntityUtils.toString(res.getEntity()));

			getAndAssert(200,"http://localhost:2004/restnames/name.groovy?name=Pia",null);
			getAndAssert(200,"http://localhost:2004/restnames/name.groovy?name=Pia",null);
			getAndAssert(429,"http://localhost:2004/restnames/name.groovy?name=Pia",null);
		}
	}

	private String getContentTypeValue(HttpResponse res) {
		return res.getEntity().getContentType().getValue();
	}

	private InputStream getStepsProxiesAsStream() {
		return getClass().getClassLoader().getResourceAsStream("com/predic8/membrane/examples/tutorials/rest/rest-tutorial-steps-proxies.xml");
	}
}
