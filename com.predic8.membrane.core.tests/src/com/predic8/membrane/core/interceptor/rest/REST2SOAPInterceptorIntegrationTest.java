/* Copyright 2011 predic8 GmbH, www.predic8.com

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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
public class REST2SOAPInterceptorIntegrationTest {

	private static HttpRouter router;

	
	@Before
	public void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "*", ".*", 8000), "www.thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
				
		REST2SOAPInterceptor rest2SoapInt = new REST2SOAPInterceptor();
		rest2SoapInt.setMappings(getMappings());
		router.getTransport().getInterceptors().add(rest2SoapInt);		
	}

	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}

	@Test
	public void testRest() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		GetMethod get = new GetMethod("http://localhost:8000/bank/37050198");
		
		int status = client.executeMethod(get);
		System.out.println(get.getResponseBodyAsString());
		
		assertEquals(200, status);			    
	}
	
	private Map<String, Map<String, String>> getMappings() {
		Map<String, Map<String, String> > mappings = new HashMap<String, Map<String, String>>();
		mappings.put("/bank/.*", getBLZMapping());
		return mappings;
	}

	private Map<String, String> getBLZMapping() {
		Map<String,String> mapping = new HashMap<String, String>();
		mapping.put("SOAPAction", "");
		mapping.put("SOAPURI", "/axis2/services/BLZService");
		mapping.put("requestXSLT", "classpath:/blz-httpget2soap-request.xsl");
		mapping.put("responseXSLT", "classpath:/strip-soap-envelope.xsl");
		return mapping;
	}	
}
