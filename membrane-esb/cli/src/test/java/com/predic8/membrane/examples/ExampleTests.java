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
package com.predic8.membrane.examples;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.predic8.membrane.examples.tests.ACLTest;
import com.predic8.membrane.examples.tests.CBRTest;
import com.predic8.membrane.examples.tests.CustomInterceptorTest;
import com.predic8.membrane.examples.tests.LoggingTest;
import com.predic8.membrane.examples.tests.QuickstartRESTTest;
import com.predic8.membrane.examples.tests.QuickstartSOAPTest;

@RunWith(Suite.class)
@SuiteClasses({ 
	ACLTest.class,
	CBRTest.class,
	CustomInterceptorTest.class,
	LoggingTest.class,
	QuickstartRESTTest.class,
	QuickstartSOAPTest.class
})
public class ExampleTests {}
