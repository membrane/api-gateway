/* Copyright 2014 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class UnavailableSoapProxyTest {

	private Router r, r2;
	private static Router router;
	private SOAPProxy sp;
	private ServiceProxy sp3;

	@BeforeAll
	static void setup() throws Exception {
		ServiceProxy rule = new ServiceProxy(new ServiceProxyKey(4000), null, 0);
		rule.getInterceptors().add(new SampleSoapServiceInterceptor());
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
		router.init();
	}

	@AfterAll
	static void teardown() {
		router.shutdown();
	}

	@BeforeEach
	void startRouter() {
		r = new Router();
		HttpClientConfiguration httpClientConfig = new HttpClientConfiguration();
		httpClientConfig.setMaxRetries(1);
		r.setHttpClientConfig(httpClientConfig);
		r.setHotDeploy(false);
		r.setRetryInit(true);

		sp = new SOAPProxy();
		sp.setPort(2000);
		sp.setWsdl("http://localhost:2001?wsdl");

		sp3 = new ServiceProxy();
		sp3.setPort(2000);
		sp3.setTarget(new AbstractServiceProxy.Target("localhost", 2001));
		ValidatorInterceptor v = new ValidatorInterceptor();
		v.setWsdl("http://localhost:2001?wsdl");
		sp3.getInterceptors().add(v);

		SOAPProxy sp2 = new SOAPProxy();
		sp2.setPort(2001);
		sp2.setWsdl("http://localhost:4000?wsdl");
		r2 = new Router();
		r2.setHotDeploy(false);
		r2.getRules().add(sp2);
	}

	@AfterEach
	void shutdownRouter() {
		r.shutdown();
		r2.shutdown();
	}

	private void test() {
		r.start();

		List<Proxy> proxies = r.getRuleManager().getRules();
		assertEquals(1, proxies.size());
		assertFalse(proxies.getFirst().isActive());
		r.tryReinitialization();

		proxies = r.getRuleManager().getRules();
		assertEquals(1, proxies.size());
		assertFalse(proxies.getFirst().isActive());
		r2.start();
		r.tryReinitialization();

		proxies = r.getRuleManager().getRules();
		assertEquals(1, proxies.size());
		assertTrue(proxies.getFirst().isActive());
	}

	@Test
	void checkWSDLDownloadFailureInSoapProxy() {
		r.getRules().add(sp);
		test();
	}

	@Test
	void checkWSDLDownloadFailureInSoapProxyAndValidator() {
		sp.getInterceptors().add(new ValidatorInterceptor());
		r.getRules().add(sp);
		test();
	}

	@Test
	void checkWSDLDownloadFailureInValidatorOfServiceProxy() {
		r.getRules().add(sp3);
		test();
	}
}
