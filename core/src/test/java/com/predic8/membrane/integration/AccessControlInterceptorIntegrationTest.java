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
package com.predic8.membrane.integration;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

public class AccessControlInterceptorIntegrationTest {

	private static HttpRouter router;

	@BeforeEach
	public void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3008), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
	}

	@AfterEach
	public void tearDown() throws Exception {
		router.shutdown();
	}

	private void setInterceptor(String fileName) throws Exception {
		AccessControlInterceptor interceptor = new AccessControlInterceptor();
		interceptor.setFile(fileName);
		router.addUserFeatureInterceptor(interceptor);
		router.init();
	}

	private PostMethod getBLZRequestMethod() {
		PostMethod post = new PostMethod("http://localhost:3008/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");

		assert stream != null;
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity);
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "\"\"");
		return post;
	}

	private HttpClient getClient(byte[] ip) throws UnknownHostException {
		HttpClient client = new HttpClient();
		HostConfiguration config = new HostConfiguration();
		config.setLocalAddress(InetAddress.getByAddress(ip));
		client.setHostConfiguration(config);
		return client;
	}

}
