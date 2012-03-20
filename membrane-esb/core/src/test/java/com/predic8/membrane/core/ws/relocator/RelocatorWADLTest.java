/* Copyright 2010 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.ws.relocator;

import static com.predic8.membrane.core.Constants.WADL_NS;

import java.io.*;
import java.util.Iterator;

import javax.xml.namespace.*;
import javax.xml.xpath.*;

import junit.framework.TestCase;

import org.xml.sax.InputSource;

public class RelocatorWADLTest extends TestCase {

	NamespaceContext nsCtx = new NamespaceContext() {
		public String getNamespaceURI(String prefix) {
			if (prefix.equals("wadl")) {
				return "http://wadl.dev.java.net/2009/02";
			}
			return null;
		}

		// Dummy implementation - not used!
		public Iterator<?> getPrefixes(String val) {
			return null;
		}

		// Dummy implemenation - not used!
		public String getPrefix(String uri) {
			return null;
		}
	};

	private Relocator relocator;
	private StringWriter writer;
	private XPath xpath = XPathFactory.newInstance().newXPath();

	@Override
	protected void setUp() throws Exception {

		xpath.setNamespaceContext(nsCtx);

		writer = new StringWriter();

		relocator = new Relocator(writer, "http", "localhost", 3000);
		relocator.getRelocatingAttributes().put(
				new QName(WADL_NS, "resources"), "base");
		relocator.getRelocatingAttributes().put(new QName(WADL_NS, "include"),
				"href");

		super.setUp();
	}

	public void testWADLRelocate() throws Exception {
		InputStreamReader wadl = new InputStreamReader(getClass()
				.getResourceAsStream("/wadls/search.wadl"));

		relocator.relocate(wadl);

		System.out.println(writer.toString());

		assertAttribute("//wadl:resources/@base",
				"http://localhost:3000/search/V1/");

		assertAttribute("//wadl:resource/@path", "newsSearch");

		assertAttribute("//wadl:grammars/wadl:include[1]/@href",
				"http://localhost:3000/search.xsd");

		assertAttribute("//wadl:grammars/wadl:include[2]/@href",
				"http://localhost:3000/error/Error.xsd");

	}

	private void assertAttribute(String xpathExpr, String expected)
			throws XPathExpressionException {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(
				new StringReader(writer.toString()))));
	}
}
