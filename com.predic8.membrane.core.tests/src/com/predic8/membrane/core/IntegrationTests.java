package com.predic8.membrane.core;


import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.predic8.membrane.core.http.MethodTest;
import com.predic8.membrane.core.interceptor.RegExReplaceInterceptorTest;
import com.predic8.membrane.core.interceptor.ValidateSOAPMsgInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.SimpleURLRewriteInterceptorIntegrationTest;
import com.predic8.membrane.integration.AccessControlInterceptorIntegrationTest;
import com.predic8.membrane.integration.Http10Test;
import com.predic8.membrane.integration.Http11Test;
import com.predic8.membrane.integration.ProxyRuleTest;
import com.predic8.membrane.interceptor.LoadBalancingInterceptorTest;

@RunWith(Suite.class)
@SuiteClasses( { 
		MethodTest.class, 
		RegExReplaceInterceptorTest.class, 
		Http10Test.class,
		Http11Test.class,
		ProxyRuleTest.class,
		AccessControlInterceptorIntegrationTest.class,
		LoadBalancingInterceptorTest.class,
		SimpleURLRewriteInterceptorIntegrationTest.class,
		ValidateSOAPMsgInterceptorTest.class
})
public class IntegrationTests {

}
