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
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Mapping;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptor;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.rules.Rule;
public class RESTBLZServiceIntegrationTest {

	private static HttpRouter router;

	
	@Before
	public void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3005), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
		
		
		HTTP2XMLInterceptor http2xml = new HTTP2XMLInterceptor();
		router.getTransport().getInterceptors().add(http2xml);

		RewriteInterceptor urlRewriter = new RewriteInterceptor();
		List<Mapping> mappings = new ArrayList<Mapping>();
		mappings.add( new Mapping("/bank/.*", "/axis2/services/BLZService", null));
		urlRewriter.setMappings(mappings);
		router.getTransport().getInterceptors().add(urlRewriter);
		
		XSLTInterceptor xslt = new XSLTInterceptor();
		xslt.setXslt("classpath:/blz-httpget2soap-request.xsl");
		xslt.setFlow(Flow.Set.REQUEST);
		xslt.setXslt("classpath:/strip-soap-envelope.xsl");
		xslt.setFlow(Flow.Set.RESPONSE);
		router.getTransport().getInterceptors().add(xslt);
		
	}

	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}

	@Test
	public void testRest() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		GetMethod get = new GetMethod("http://localhost:3005/bank/37050198");
		
		int status = client.executeMethod(get);
		System.out.println(get.getResponseBodyAsString());
		
		assertEquals(200, status);			    
	}	
}
