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

import com.predic8.membrane.examples.tests.LoadBalancerClient2Test;
import com.predic8.membrane.examples.tests.LoadBalancerSession3Test;
import com.predic8.membrane.examples.tests.LoggingCSVTest;
import com.predic8.membrane.examples.tests.QuickstartRESTTest;
import com.predic8.membrane.examples.tests.REST2SOAPJSONTest;
import com.predic8.membrane.examples.tests.validation.SOAPCustomValidationTest;

@RunWith(Suite.class)
@SuiteClasses({ 
	LoadBalancerClient2Test.class,
	LoadBalancerSession3Test.class,
	LoggingCSVTest.class,
	QuickstartRESTTest.class,
	REST2SOAPJSONTest.class,

	SOAPCustomValidationTest.class
})
public class ExampleTestsFailingOnJenkins {}
