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
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class DispatchingInterceptorTest {

	private DispatchingInterceptor dispatcher;
	private ServiceProxy serviceProxy;

	private Exchange exc;

	@BeforeEach
	public void setUp() throws Exception {
		Router router = new Router();
		dispatcher = new DispatchingInterceptor();
		dispatcher.init(router);
		exc = new Exchange(null);
		serviceProxy = new ServiceProxy(new ServiceProxyKey("localhost", ".*", ".*", 3011), "thomas-bayer.com", 80);
	}

	@Test
	public void testServiceProxy() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("/axis2/services/BLZService?wsdl"));
		exc.setRule(serviceProxy);

		assertEquals(CONTINUE, dispatcher.handleRequest(exc));

		URL url = new URL(exc.getDestinations().getFirst());
		assertEquals(80, url.getPort());
		assertEquals("thomas-bayer.com", url.getHost());
		assertEquals("/axis2/services/BLZService?wsdl", url.getFile());
	}

	@Test
	public void testProxyRuleHttp() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("http://www.thomas-bayer.com:80/axis2/services/BLZService?wsdl"));
		exc.setRule(getProxyRule());

		assertEquals(CONTINUE, dispatcher.handleRequest(exc));

		URL url = new URL(exc.getDestinations().getFirst());

		assertEquals(80, url.getPort());
		assertEquals("www.thomas-bayer.com", url.getHost());
		assertEquals("/axis2/services/BLZService?wsdl", url.getFile());
	}

	private ProxyRule getProxyRule() {
		return new ProxyRule(new ProxyRuleKey(3090));
	}

    @Test
    void getAddressFromTargetElementTargetWithHostAndPort() throws Exception {
		exc.setRule(serviceProxy);
		exc.setRequest(new Request.Builder().get("/foo").build());
		assertEquals("http://thomas-bayer.com:80/foo", getGetAddressFromTargetElement());
    }

	@Test
	void getAddressFromTargetElementTargetWithURL() throws Exception {
		serviceProxy.setTargetURL("http://api.predic8.de");
		exc.setRule(serviceProxy);
		exc.setOriginalRequestUri("/foo");
		assertEquals("http://api.predic8.de/foo", getGetAddressFromTargetElement());
	}

	@Test
	void getAddressFromTargetElementTargetWithURLHTTPS() throws Exception {
		serviceProxy.setTargetURL("https://api.predic8.de");
		exc.setRule(serviceProxy);
		exc.setOriginalRequestUri("/foo");
		assertEquals("https://api.predic8.de/foo", getGetAddressFromTargetElement());
	}

	@Test
	void getAddressFromTargetElementTargetWithSlash() throws Exception {
		serviceProxy.setTargetURL("https://api.predic8.de/");
		exc.setRule(serviceProxy);
		exc.setOriginalRequestUri("/foo");
		assertEquals("https://api.predic8.de/", getGetAddressFromTargetElement());
	}

	@Test
	void getAddressFromTargetElementTargetWithPath() throws Exception {
		serviceProxy.setTargetURL("https://api.predic8.de/baz");
		exc.setRule(serviceProxy);
		exc.setOriginalRequestUri("/foo");
		assertEquals("https://api.predic8.de/baz", getGetAddressFromTargetElement());
	}

	@Nullable
	private String getGetAddressFromTargetElement() throws Exception {
		DispatchingInterceptor di = new DispatchingInterceptor();
		di.init(new Router());
		return di.getAddressFromTargetElement( exc);
	}
}