/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xmlcontentfilter;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.test.StringAssertions.assertContains;
import static com.predic8.membrane.test.StringAssertions.assertContainsNot;

class SOAPStackTraceFilterTest {

	private Request getRequest() throws IOException {
		Request r = new Request();
		r.setBody(new Body(getClass().getResourceAsStream("/soap-sample/soapfault-with-java-stacktrace.xml")));
		return r;
	}

	@Test
	void doit() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setRequest(getRequest());
		new SOAPStackTraceFilterInterceptor().handleRequest(exc);
		String body = exc.getRequest().getBody().toString();
		assertContainsNot("MyService", body);
		assertContains("soap:Server", body);
		assertContainsNot("stacktrace", body);
	}

}
