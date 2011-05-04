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

import com.predic8.membrane.core.http.BodyTest;
import com.predic8.membrane.core.http.HeaderTest;
import com.predic8.membrane.core.http.RequestTest;
import com.predic8.membrane.core.http.ResponseTest;
import com.predic8.membrane.core.interceptor.AbstractInterceptorTest;
import com.predic8.membrane.core.interceptor.DispatchingInterceptorTest;
import com.predic8.membrane.core.interceptor.WSDLInterceptorTest;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptorTest;
import com.predic8.membrane.core.interceptor.rest.HTTP2XMLInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.SimpleURLRewriteInterceptorTest;
import com.predic8.membrane.core.magic.MagicTest;
import com.predic8.membrane.core.transport.http.HostColonPortTest;
import com.predic8.membrane.core.util.ByteUtilTest;
import com.predic8.membrane.core.util.HttpUtilTest;

@RunWith(Suite.class)
@SuiteClasses( { 
	HeaderTest.class,
	BodyTest.class,
	ByteUtilTest.class,
	HttpUtilTest.class,
	RequestTest.class,
	ResponseTest.class,
	MagicTest.class,
	CoreActivatorTest.class,	
	WSDLInterceptorTest.class,
	AccessControlInterceptorTest.class,
	DispatchingInterceptorTest.class,
	SimpleURLRewriteInterceptorTest.class,
	AbstractInterceptorTest.class,
	HostColonPortTest.class,
	HTTP2XMLInterceptorTest.class,	
	ReadRulesConfigurationTest.class,
	ReadRulesInterceptorRefTest.class
})
public class UnitlTests {

}
