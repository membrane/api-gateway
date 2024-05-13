/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.integration.ACL;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.interceptor.misc.SetHeaderInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.predic8.membrane.core.interceptor.acl.ParseType.GLOB;
import static java.util.regex.Pattern.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleForwardingTest {
	private static HttpRouter router;

	private static final GetMethod GET = new GetMethod("http://localhost:5005");

	@BeforeEach
	public void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "GET", ".*", 5005), "www.google.de", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
	}

	@AfterEach
	public void tearDown() throws Exception {
		router.shutdown();
	}

	@Test
	public void matchesIp() throws Exception {
		initRouter(getIpResource("10.10.10.10", GLOB));
		assertEquals(200, getClient().executeMethod(GET));
	}

	@Test
	public void notmatchesIp() throws Exception {
		initRouter(getIpResource("127.0.0.1", GLOB));
		assertEquals(401, getClient().executeMethod(GET));
	}

	private void initRouter(Resource r) throws Exception {
		router.addUserFeatureInterceptor(buildShi());
		router.addUserFeatureInterceptor(buildAci(r));
		router.init();
	}

	private static @NotNull SetHeaderInterceptor buildShi() {
		return new SetHeaderInterceptor() {{
			setName("X-Forwarded-For");
			setValue("10.10.10.10, 127.0.0.1");
		}};
	}

	private AccessControlInterceptor buildAci(Resource r) {
		return new ManualProxyTest(){{
			setAccessControl(
					new AccessControl(router) {{
						addResource(r);
						setUseXForwardedForAsClientAddr(true);
					}}
			);
		}};
	}

	private Resource getIpResource(String scheme, ParseType ptype) {
		return getResource(new Ip(router) {{
			setParseType(ptype);
			setSchema(scheme);
		}});
	}

	private Resource getResource(AbstractClientAddress addr) {
		return new Resource(router) {{
			addAddress(addr);
			setUriPattern(compile(".*"));
		}};
	}

	private HttpClient getClient() throws UnknownHostException {
		HttpClient client = new HttpClient();
		HostConfiguration config = new HostConfiguration();
		config.setLocalAddress(InetAddress.getByAddress(new byte[]{ (byte)127, (byte)0, (byte)0,  (byte)1 }));
		client.setHostConfiguration(config);
		return client;
	}
}