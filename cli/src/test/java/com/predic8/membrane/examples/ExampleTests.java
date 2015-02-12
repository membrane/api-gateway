/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.predic8.membrane.examples.env.AntInPath;
import com.predic8.membrane.examples.env.ConsistentVersionNumbers;
import com.predic8.membrane.examples.env.DefaultConfigTest;
import com.predic8.membrane.examples.env.HelpLinkExistenceTest;
import com.predic8.membrane.examples.env.JavaLicenseInfoTest;
import com.predic8.membrane.examples.tests.ACLTest;
import com.predic8.membrane.examples.tests.BasicAuthTest;
import com.predic8.membrane.examples.tests.CBRTest;
import com.predic8.membrane.examples.tests.CustomInterceptorTest;
import com.predic8.membrane.examples.tests.FileExchangeStoreTest;
import com.predic8.membrane.examples.tests.GroovyTest;
import com.predic8.membrane.examples.tests.LoadBalancerBasic1Test;
import com.predic8.membrane.examples.tests.LoadBalancerClient2Test;
import com.predic8.membrane.examples.tests.LoadBalancerMultiple4Test;
import com.predic8.membrane.examples.tests.LoadBalancerSession3Test;
import com.predic8.membrane.examples.tests.LoadBalancerStaticTest;
import com.predic8.membrane.examples.tests.LoggingCSVTest;
import com.predic8.membrane.examples.tests.LoggingJDBCTest;
import com.predic8.membrane.examples.tests.LoggingTest;
import com.predic8.membrane.examples.tests.LoginTest;
import com.predic8.membrane.examples.tests.QuickstartRESTTest;
import com.predic8.membrane.examples.tests.QuickstartSOAPTest;
import com.predic8.membrane.examples.tests.REST2SOAPJSONTest;
import com.predic8.membrane.examples.tests.REST2SOAPTest;
import com.predic8.membrane.examples.tests.RewriterTest;
import com.predic8.membrane.examples.tests.SSLClient;
import com.predic8.membrane.examples.tests.SSLServer;
import com.predic8.membrane.examples.tests.ThrottleTest;
import com.predic8.membrane.examples.tests.XSLTTest;
import com.predic8.membrane.examples.tests.validation.FormValidationTest;
import com.predic8.membrane.examples.tests.validation.JSONSchemaValidationTest;
import com.predic8.membrane.examples.tests.validation.SchematronValidationTest;
import com.predic8.membrane.examples.tests.validation.XMLValidationTest;
import com.predic8.membrane.examples.tests.versioning.RoutingTest;

@RunWith(Suite.class)
@SuiteClasses({ 
	AntInPath.class,
	ConsistentVersionNumbers.class,
	DefaultConfigTest.class,
	HelpLinkExistenceTest.class,
	JavaLicenseInfoTest.class,
	
	ACLTest.class,
	BasicAuthTest.class,
	CBRTest.class,
	CustomInterceptorTest.class,
	FileExchangeStoreTest.class,
	GroovyTest.class,
	LoadBalancerBasic1Test.class,
	LoadBalancerClient2Test.class,
	LoadBalancerMultiple4Test.class,
	LoadBalancerSession3Test.class,
	LoadBalancerStaticTest.class,
	LoggingCSVTest.class,
	LoggingJDBCTest.class,
	LoggingTest.class,
	LoginTest.class,
	QuickstartRESTTest.class,
	QuickstartSOAPTest.class,
	REST2SOAPTest.class,
	REST2SOAPJSONTest.class,
	RewriterTest.class,
	SSLServer.class,
	SSLClient.class,
	ThrottleTest.class,
	XSLTTest.class,
	
	FormValidationTest.class,
	JSONSchemaValidationTest.class,
	SchematronValidationTest.class,
	XMLValidationTest.class,
	
	RoutingTest.class
})
public class ExampleTests {}
