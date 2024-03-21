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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor.Mapping;
import com.predic8.membrane.core.interceptor.soap.SampleSoapServiceInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.apache.http.params.CoreProtocolPNames.PROTOCOL_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class REST2SOAPInterceptorIntegrationTest {

	private static HttpRouter router;

	@BeforeAll
	public static void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "*",
				".*", 3004), "", 0);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
		var interceptors = rule.getInterceptors();

		REST2SOAPInterceptor rest2SoapInt = new REST2SOAPInterceptor();
		rest2SoapInt.setMappings(getMappings());
		interceptors.add(rest2SoapInt);

		SampleSoapServiceInterceptor sampleSoapInt = new SampleSoapServiceInterceptor();
		interceptors.add(sampleSoapInt);
		router.init();
	}

	@AfterAll
	public static void tearDown() throws Exception {
		router.shutdown();
	}

	@Test
	public void testRest() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		GetMethod get = new GetMethod("http://localhost:3004/city/Bonn");

		int status = client.executeMethod(get);
		System.out.println(get.getResponseBodyAsString());

		assertEquals(200, status);
	}

	private static List<Mapping> getMappings() {
		List<Mapping> mappings = new ArrayList<>();
		Mapping m = new Mapping();
		m.regex = "/city/.*";
		m.soapAction = "";
		m.soapURI = "/city-service";
		m.requestXSLT = "classpath:/blz-httpget2soap-request.xsl";
		m.responseXSLT = "classpath:/strip-soap-envelope.xsl";
		mappings.add(m);
		return mappings;
	}
}
