/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.xslt;

import java.io.*;

import javax.xml.xpath.*;

import junit.framework.TestCase;

import org.junit.Test;
import org.xml.sax.InputSource;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptor;


public class XSLTInterceptorTest extends TestCase {
		
	Exchange exc = new Exchange();
	XPath xpath = XPathFactory.newInstance().newXPath();
	
	@Test
	public void testRequest() throws Exception {
		exc = new Exchange();
		Response res = new Response();		
		res.setBodyContent(getBodyContent());
		exc.setResponse(res);

		XSLTInterceptor i = new XSLTInterceptor();
		i.setResponseXSLT("classpath:/xml2html.xsl");
		i.handleResponse(exc);
				
		printBodyContent();
		assertXPath("//tr[2]/td[1]","-20");
		assertXPath("//tr[2]/td[2]","Rick");
		assertXPath("//tr[2]/td[3]","Cortés Ribotta");
		assertXPath("//tr[2]/td[4]","Calle Pública \"B\" 5240 Casa 121");
		assertXPath("//tr[2]/td[5]","Omaha");
	}
	
	private void printBodyContent() throws Exception {
		InputStream i = exc.getResponse().getBodyAsStream();
		int read = 0;
		byte[] buf = new byte[4096];
		while ((read = i.read(buf)) != -1) {
			System.out.write(buf, 0, read);
		}
	}
	
	private byte[] getBodyContent() throws IOException {
		byte[] buf = new byte[195];
		getClass().getResourceAsStream("/customer.xml").read(buf);
		return buf;
	}

	private void assertXPath(String xpathExpr, String expected) throws XPathExpressionException {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(exc.getResponse().getBodyAsStream())));
	}
	
}
