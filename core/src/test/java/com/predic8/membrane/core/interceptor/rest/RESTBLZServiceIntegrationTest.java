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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.*;
import com.predic8.membrane.core.interceptor.xslt.*;
import com.predic8.membrane.core.rules.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.http.params.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RESTBLZServiceIntegrationTest {

	private static HttpRouter router;

	@BeforeAll
	static void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3005), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);


		HTTP2XMLInterceptor http2xml = new HTTP2XMLInterceptor();
		router.getTransport().getInterceptors().add(http2xml);

		RewriteInterceptor urlRewriter = new RewriteInterceptor();
		List<Mapping> mappings = new ArrayList<>();
		mappings.add( new Mapping("/bank/.*", "/axis2/services/BLZService", null));
		urlRewriter.setMappings(mappings);
		router.getTransport().getInterceptors().add(urlRewriter);

		XSLTInterceptor xslt = new XSLTInterceptor();
		xslt.setXslt("classpath:/blz-httpget2soap-request.xsl");
		xslt.setFlow(Flow.Set.REQUEST);
		xslt.setXslt("classpath:/strip-soap-envelope.xsl");
		xslt.setFlow(Flow.Set.RESPONSE_ABORT);
		router.getTransport().getInterceptors().add(xslt);

	}

	@AfterAll
	static void tearDown() throws Exception {
		router.shutdown();
	}

	@Test
	void testRest() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		GetMethod get = new GetMethod("http://localhost:3005/bank/37050198");

		int status = client.executeMethod(get);
		System.out.println(get.getResponseBodyAsString());

		assertEquals(200, status);
	}
}
