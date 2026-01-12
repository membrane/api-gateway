/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.proxies.AbstractServiceProxy.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class UnavailableSoapProxyTest {

	private static DefaultRouter r, r2;
	private static DefaultRouter backendRouter;
	private SOAPProxy sp;
	private ServiceProxy sp3;

	@BeforeAll
	static void setup() throws Exception {
		ServiceProxy cityAPI = new ServiceProxy(new ServiceProxyKey(4000), null, 0);
		cityAPI.getFlow().add(new SampleSoapServiceInterceptor());
		backendRouter = new DefaultRouter();
		backendRouter.add(cityAPI);
		backendRouter.start();
	}

	@AfterAll
	static void teardown() {
		backendRouter.stop();
		r.stop();
		r2.stop();
	}

	@BeforeEach
	void startRouter() throws IOException {
		r = new DefaultRouter();
		HttpClientConfiguration httpClientConfig = new HttpClientConfiguration();
		httpClientConfig.getRetryHandler().setRetries(1);
		r.setHttpClientConfig(httpClientConfig);
		r.getConfiguration().setHotDeploy(false);
		r.getConfiguration().setRetryInit(true);

		sp = new SOAPProxy();
		sp.setPort(2000);
		sp.setWsdl("http://localhost:2001?wsdl");

		sp3 = new ServiceProxy();
		sp3.setPort(2000);
		sp3.setTarget(new Target("localhost", 2001));
		ValidatorInterceptor v = new ValidatorInterceptor(); // Calling init on interceptor will break test!
		v.setWsdl("http://localhost:2001?wsdl");
		sp3.getFlow().add(v);

		SOAPProxy sp2 = new SOAPProxy();
		sp2.setPort(2001);
		sp2.setWsdl("http://localhost:4000?wsdl");
		r2 = new DefaultRouter();
		r2.getConfiguration().setHotDeploy(false);
		r2.add(sp2);
	}

	@AfterEach
	void teardownEach() {
		r.stop();
		r2.stop();
	}

	private void test() {
		r.start();

		List<Proxy> proxies = r.getRuleManager().getRules();
		assertEquals(1, proxies.size());
		assertFalse(proxies.getFirst().isActive());
		r.getReinitializer().retry();

		proxies = r.getRuleManager().getRules();
		assertEquals(1, proxies.size());
		assertFalse(proxies.getFirst().isActive());
		r2.start();
		r.getReinitializer().retry();

		proxies = r.getRuleManager().getRules();
		assertEquals(1, proxies.size());
		assertTrue(proxies.getFirst().isActive());
	}

	@Test
	void checkWSDLDownloadFailureInSoapProxy() throws IOException {
		r.add(sp);
		test();
	}

	@Test
	void checkWSDLDownloadFailureInSoapProxyAndValidator() throws IOException {
		sp.getFlow().add(new ValidatorInterceptor());
		r.add(sp);
		test();
	}

	@Test
	void checkWSDLDownloadFailureInValidatorOfServiceProxy() throws IOException {
		r.add(sp3);
		test();
	}
}
