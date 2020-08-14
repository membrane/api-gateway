/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xslt;

import java.io.InputStream;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import junit.framework.TestCase;

import org.junit.Test;
import org.xml.sax.InputSource;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;

public class XSLTInterceptorTest extends TestCase {

	Exchange exc = new Exchange(null);
	XPath xpath = XPathFactory.newInstance().newXPath();

	@Test
	public void testRequest() throws Exception {
		exc = new Exchange(null);
		exc.setResponse(Response.ok().body(getClass().getResourceAsStream("/customer.xml"), true).build());

		XSLTInterceptor i = new XSLTInterceptor();
		i.setXslt("classpath:/customer2person.xsl");
		i.init(new HttpRouter());
		i.handleResponse(exc);

		//printBodyContent();
		assertXPath("/person/name/first", "Rick");
		assertXPath("/person/name/last", "Cort\u00e9s Ribotta");
		assertXPath("/person/address/street",
				"Calle P\u00fablica \"B\" 5240 Casa 121");
		assertXPath("/person/address/city", "Omaha");
	}

	@Test
	public void testXSLTParameter() throws Exception {
		exc = new Exchange(null);
		exc.setResponse(Response.ok().body(getClass().getResourceAsStream("/customer.xml"), true).build());

		exc.setProperty("XSLT_COMPANY", "predic8");

		XSLTInterceptor i = new XSLTInterceptor();
		i.setXslt("classpath:/customer2personAddCompany.xsl");
		i.init(new HttpRouter());
		i.handleResponse(exc);

		//printBodyContent();
		assertXPath("/person/name/first", "Rick");
		assertXPath("/person/name/last", "Cort\u00e9s Ribotta");
		assertXPath("/person/address/street",
				"Calle P\u00fablica \"B\" 5240 Casa 121");
		assertXPath("/person/address/city", "Omaha");
		assertXPath("/person/company", "predic8");
	}

	@SuppressWarnings("unused")
	private void printBodyContent() throws Exception {
		InputStream i = exc.getResponse().getBodyAsStream();
		int read = 0;
		byte[] buf = new byte[4096];
		while ((read = i.read(buf)) != -1) {
			System.out.write(buf, 0, read);
		}
	}

	private void assertXPath(String xpathExpr, String expected)
			throws XPathExpressionException {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(exc
				.getResponse().getBodyAsStream())));
	}

}
