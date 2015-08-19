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

import java.io.IOException;

import javax.xml.xpath.XPathExpressionException;

import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.test.AssertUtils;

public class SOAPStackTraceFilterTest {

	private Request getRequest() throws IOException {
		Request r = new Request();
		r.setBody(new Body(getClass().getResourceAsStream("/xml/soap-stack-trace.xml")));
		return r;
	}

	@Test
	public void doit() throws XPathExpressionException, Exception {
		Exchange exc = new Exchange(null);
		exc.setRequest(getRequest());
		new SOAPStackTraceFilterInterceptor().handleRequest(exc);
		String body = exc.getRequest().getBody().toString();
		AssertUtils.assertContainsNot("SECRET", body);
		AssertUtils.assertContains("KEEP1", body);
	}

}
