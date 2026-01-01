/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.integration.withinternet.interceptor;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static org.apache.commons.httpclient.HttpVersion.HTTP_1_1;
import static org.apache.http.params.CoreProtocolPNames.PROTOCOL_VERSION;
import static org.junit.jupiter.api.Assertions.*;

public class RewriteInterceptorIntegrationTest {

	private static Router router;

	String soap = """
			<s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cit="https://predic8.de/cities">
			    <s11:Body>
			        <cit:getCity>
			            <name>Bonn</name>
			        </cit:getCity>
			    </s11:Body>
			</s11:Envelope>s
			""";

    @BeforeAll
	static void setUp() throws Exception {

        RewriteInterceptor interceptor = new RewriteInterceptor();
		interceptor.getMappings().add(new Mapping("/city\\?wsdl", "/city-service?wsdl", null));

		ServiceProxy proxy = new APIProxy();
		AbstractServiceProxy.Target target = new AbstractServiceProxy.Target();
		target.setUrl("https://www.predic8.de");
		proxy.setTarget(target);
		proxy.setKey(new ServiceProxyKey("localhost", "*", ".*", 3006));
		proxy.getFlow().add(interceptor);

		router = new TestRouter();
		router.add(proxy);
		router.start();
	}

	@AfterAll
	static void tearDown() {
		router.stop();
	}

	@Test
	void testRewriting() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(PROTOCOL_VERSION, HTTP_1_1);
		var post = getPostMethod();
		int status = client.executeMethod(post);
		assertEquals(200, status);
		assertTrue(post.getResponseBodyAsString().contains("CitySoapBinding"));
	}

	private PostMethod getPostMethod() {
		PostMethod post = new PostMethod("http://localhost:3006/city?wsdl");
		post.setRequestEntity(new StringRequestEntity(soap));
		post.setRequestHeader(CONTENT_TYPE, TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");
		return post;
	}
}
