package com.predic8.membrane.core.interceptor.xmlprotection;

import static junit.framework.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.MessageUtil;

public class XMLProtectionInterceptorTest {
	private Exchange exc;
	private XMLProtectionInterceptor interceptor;

	private Rule getRule() {		
		return new ServiceProxy(new ServiceProxyKey("localhost", ".*", ".*", 3011), "thomas-bayer.com", 80);
	}

	@Before
	public void setUp() throws Exception {
		exc = new Exchange();
		exc.setRequest(MessageUtil.getGetRequest("/axis2/services/BLZService"));
		exc.setOriginalHostHeader("thomas-bayer.com:80");
		exc.setRule(getRule());
		
		interceptor = new XMLProtectionInterceptor();
	}

	private void runOn(String resource, boolean expectSuccess) throws Exception {
		exc.getRequest().getHeader().setContentType("application/xml");
		exc.getRequest().setBodyContent(ByteUtil.getByteArrayData(this.getClass().getResourceAsStream(resource)));
		Outcome outcome = interceptor.handleRequest(exc);
		assertEquals(expectSuccess ? Outcome.CONTINUE : Outcome.ABORT, outcome);
	}
	
	@Test
	public void testInvariant() throws Exception {
		runOn("/customer.xml", true);
	}

	@Test
	public void testNotWellformed() throws Exception {
		runOn("/xml/not-wellformed.xml", false);
	}


}
