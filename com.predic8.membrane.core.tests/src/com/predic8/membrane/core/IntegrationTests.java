package com.predic8.membrane.core;

import junit.framework.Test;
import junit.framework.TestSuite;

import com.predic8.membrane.integration.AccessControlInterceptorTest;
import com.predic8.membrane.integration.Http10Test;
import com.predic8.membrane.integration.Http11Test;
import com.predic8.membrane.integration.ProxyRuleTest;

public class IntegrationTests {

	
	public static Test suite() {
		TestSuite suite = new TestSuite("Integration tests for com.predic8.membrane.core");
		//$JUnit-BEGIN$
		suite.addTestSuite(Http10Test.class);
		suite.addTestSuite(Http11Test.class);
		suite.addTestSuite(ProxyRuleTest.class);
		suite.addTestSuite(AccessControlInterceptorTest.class);
		//$JUnit-END$
		return suite;
	}

}
