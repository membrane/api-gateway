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

import junit.framework.Assert;

import org.custommonkey.xmlunit.XMLAssert;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;

public class XMLContentFilterTest {
	
	private final static String DOC = "<a><b c=\"12\" d=\"&quot;\"></b></a>";
	
	private Message getMessage() {
		Message m = new Request();
		m.setBody(new Body(DOC.getBytes()));
		return m;
	}

	private String applyXPath(String xpath) throws XPathExpressionException {
		Message m = getMessage();
		new XMLContentFilter(xpath).removeMatchingElements(m);
		return m.getBody().toString();
	}

	@Test
	public void test() throws XPathExpressionException, SAXException, IOException {
		XMLAssert.assertXMLEqual("<a/>", applyXPath("//b"));
		XMLAssert.assertXMLEqual("<a/>", applyXPath("//b[@c]"));
		XMLAssert.assertXMLEqual("<a/>", applyXPath("//*[ local-name() = 'b' ]"));
		XMLAssert.assertXMLEqual(DOC, applyXPath("//b[@c='2']"));
	}
	
	public void assertFastCheck(String xpath) {
		// if createElementFinder is not null, a (fast) StAX parser can be used
		// to run a first check whether the XPath expression has any chance of succeeding
		Assert.assertNotNull(XMLContentFilter.createElementFinder(xpath));
	}
	
	@Test
	public void testPerformance() {
		// if createElementFinder is not null, a (fast) StAX parser can be used
		// to run a first check whether the XPath expression has any chance of succeeding
		assertFastCheck("//*[local-name()='Body']");
		assertFastCheck("//*[local-name()='Body' and namespace-uri()='http://schemas.xmlsoap.org/soap/envelope/']");
		assertFastCheck("//b[@c]");
		assertFastCheck("// * [ local-name() = 'b']");
	}

}
