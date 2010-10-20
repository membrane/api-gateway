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

import junit.framework.Test;
import junit.framework.TestSuite;

import com.predic8.membrane.core.http.BodyTest;
import com.predic8.membrane.core.http.HeaderTest;
import com.predic8.membrane.core.http.RequestTest;
import com.predic8.membrane.core.http.ResponseTest;
import com.predic8.membrane.core.interceptor.AbstractInterceptorTest;
import com.predic8.membrane.core.interceptor.DispatchingInterceptorTest;
import com.predic8.membrane.core.interceptor.WSDLInterceptorTest;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.SimpleURLRewriteInterceptorTest;
import com.predic8.membrane.core.magic.MagicTest;
import com.predic8.membrane.core.util.ByteUtilTest;
import com.predic8.membrane.core.util.HttpUtilTest;


public class UnitlTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("Unit tests for com.predic8.membrane.core");
		//$JUnit-BEGIN$
		suite.addTestSuite(HeaderTest.class);
		suite.addTestSuite(BodyTest.class);
		suite.addTestSuite(ByteUtilTest.class);
		suite.addTestSuite(HttpUtilTest.class);
		suite.addTestSuite(RequestTest.class);
		suite.addTestSuite(ResponseTest.class);
		suite.addTestSuite(MagicTest.class);
		suite.addTestSuite(CoreActivatorTest.class);
		suite.addTestSuite(WSDLInterceptorTest.class);
		suite.addTestSuite(AccessControlInterceptorTest.class);
		suite.addTestSuite(DispatchingInterceptorTest.class);
		suite.addTestSuite(SimpleURLRewriteInterceptorTest.class);
		suite.addTestSuite(AbstractInterceptorTest.class);
		//$JUnit-END$
		return suite;
	}
}
