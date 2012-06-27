/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.rest;

import static junit.framework.Assert.assertEquals;

import java.util.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.*;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor.Mapping;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.rules.Rule;

public class REST2SOAPInterceptorIntegrationTest {

	private static HttpRouter router;

	@Before
	public void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "*",
				".*", 3004), "www.thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);

		REST2SOAPInterceptor rest2SoapInt = new REST2SOAPInterceptor();
		rest2SoapInt.setMappings(getMappings());
		rule.getInterceptors().add(rest2SoapInt);
		router.init();
	}

	@After
	public void tearDown() throws Exception {
		router.shutdownNoWait();
	}

	@Test
	public void testRest() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION,
				HttpVersion.HTTP_1_1);
		GetMethod get = new GetMethod("http://localhost:3004/bank/37050198");

		int status = client.executeMethod(get);
		//System.out.println(get.getResponseBodyAsString());

		assertEquals(200, status);
	}

	private List<Mapping> getMappings() {
		List<Mapping> mappings = new ArrayList<Mapping>();
		Mapping m = new Mapping();
		m.regex = "/bank/.*";
		m.soapAction = "";
		m.soapURI = "/axis2/services/BLZService";
		m.requestXSLT = "classpath:/blz-httpget2soap-request.xsl";
		m.responseXSLT = "classpath:/strip-soap-envelope.xsl";
		mappings.add(m);
		return mappings;
	}
}
