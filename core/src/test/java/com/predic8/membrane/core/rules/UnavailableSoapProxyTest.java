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
package com.predic8.membrane.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;

public class UnavailableSoapProxyTest {

	private Router r, r2;
	private SOAPProxy sp;
	private ServiceProxy sp3;

	@Before
	public void setup() {
		r = new Router();
		HttpClientConfiguration httpClientConfig = new HttpClientConfiguration();
		httpClientConfig.setMaxRetries(1);
		r.setHttpClientConfig(httpClientConfig);
		r.setHotDeploy(false);
		r.setRetryInit(true);

		sp = new SOAPProxy();
		sp.setPort(2000);
		sp.setWsdl("http://localhost:2001/axis2/services/BLZService?wsdl");

		sp3 = new ServiceProxy();
		sp3.setPort(2000);
		sp3.setTarget(new AbstractServiceProxy.Target("localhost", 2001));
		ValidatorInterceptor v = new ValidatorInterceptor();
		v.setWsdl("http://localhost:2001/axis2/services/BLZService?wsdl");
		sp3.getInterceptors().add(v);

		
		SOAPProxy sp2 = new SOAPProxy();
		sp2.setPort(2001);
		sp2.setWsdl("http://www.thomas-bayer.com/axis2/services/BLZService?wsdl");
		r2 = new Router();
		r2.setHotDeploy(false);
		r2.getRules().add(sp2);
		// r2 will be started during the test

	}

	private void test() {
		r.start();
		
		List<Rule> rules = r.getRuleManager().getRules();
		assertEquals(1, rules.size());
		assertFalse(rules.get(0).isActive());

		r.tryReinitialization();

		rules = r.getRuleManager().getRules();
		assertEquals(1, rules.size());
		assertFalse(rules.get(0).isActive());

		r2.start();
		r.tryReinitialization();

		rules = r.getRuleManager().getRules();
		assertEquals(1, rules.size());
		assertTrue(rules.get(0).isActive());
	}
	
	@Test
	public void checkWSDLDownloadFailureInSoapProxy() {
		r.getRules().add(sp);

		test();
	}

	@Test
	public void checkWSDLDownloadFailureInSoapProxyAndValidator() {
		sp.getInterceptors().add(new ValidatorInterceptor());
		r.getRules().add(sp);

		test();
	}
	
	@Test
	public void checkWSDLDownloadFailureInValidatorOfServiceProxy() {
		r.getRules().add(sp3);
		
		test();
	}
	
	@After
	public void cleanup() {
		r2.stop();
		r.stop();
	}

}
