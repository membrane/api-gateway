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

	@Before
	public void setup() {
		sp = new SOAPProxy();
		sp.setPort(2000);
		sp.setWsdl("http://localhost:2001/axis2/services/BLZService?wsdl");
		r = new Router();
		HttpClientConfiguration httpClientConfig = new HttpClientConfiguration();
		httpClientConfig.setMaxRetries(1);
		r.setHttpClientConfig(httpClientConfig);
		r.setHotDeploy(false);
		r.getRules().add(sp);
		
		SOAPProxy sp2 = new SOAPProxy();
		sp2.setPort(2001);
		sp2.setWsdl("http://www.thomas-bayer.com/axis2/services/BLZService?wsdl");
		r2 = new Router();
		r2.setHotDeploy(false);
		r2.getRules().add(sp2);
		// r2 will be started during the test
	}

	@Test
	public void doit() {
		r.start();
		
		List<Rule> rules = r.getRuleManager().getRules();
		assertEquals(1, rules.size());
		assertFalse(rules.get(0).isActive());
		
		r2.start();
		r.tryReinitialization();

		rules = r.getRuleManager().getRules();
		assertEquals(1, rules.size());
		assertTrue(rules.get(0).isActive());
	}

	@Test
	public void doit2() {
		sp.getInterceptors().add(new ValidatorInterceptor());
		doit();
	}
	
	@After
	public void cleanup() {
		r2.stop();
		r.stop();
	}

}
