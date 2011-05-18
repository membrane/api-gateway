/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core;


import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.predic8.membrane.core.http.MethodTest;
import com.predic8.membrane.core.interceptor.RegExReplaceInterceptorTest;
import com.predic8.membrane.core.interceptor.ValidateSOAPMsgInterceptorTest;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptorIntegrationTest;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptorIntegrationTest;
import com.predic8.membrane.core.interceptor.rewrite.SimpleURLRewriteInterceptorIntegrationTest;
import com.predic8.membrane.core.transport.http.InterceptorInvocationTest;
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
		ValidateSOAPMsgInterceptorTest.class,
		REST2SOAPInterceptorIntegrationTest.class,
		InterceptorInvocationTest.class,
		BasicAuthenticationInterceptorIntegrationTest.class
})
public class IntegrationTests {

}
