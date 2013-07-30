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

import java.util.List;

import javax.xml.namespace.QName;

import org.junit.Assert;

import org.junit.Test;

public class SimpleXPathAnalyzerTest {

	/**
	 * Read the documentation for {@link SimpleXPathAnalyzer#getIntersectExceptExprs(String)}.
	 * 
	 * @param countToAssert the number of "IntersectExceptExpr" productions contained in the XPath expression,
	 * 	or -1 if the XPath expression is not a "UnionExpr" production.
	 */
	private void assertIntersectExceptExprCount(int countToAssert, String xpath) {
		List<?> l = new SimpleXPathAnalyzer().getIntersectExceptExprs(xpath);
		if (countToAssert == -1)
			Assert.assertNull(l);
		else
			Assert.assertEquals(countToAssert, l.size());
	}
	
	@Test
	public void testGetIntersectExceptExprs() {
		assertIntersectExceptExprCount(-1, "//x and //y");
		assertIntersectExceptExprCount( 1, "//x-and //y");
		assertIntersectExceptExprCount( 2, "//x |   //y");

		assertIntersectExceptExprCount(-1, "//x/and //y"); // note that the correct value would be 1, but due
		// to our simplified parsing this is not recognized
	}

	private void assertGetElement(String xpath, QName expectedReturnValue) {
		Assert.assertEquals(expectedReturnValue, 
				new SimpleXPathAnalyzer().getElement(new SimpleXPathParser().parse(xpath)));
	}
	
	@Test
	public void testGetElement() {
		assertGetElement("//a[@b]", new QName("a"));
		assertGetElement("//a/b[@c]", new QName("a"));
		assertGetElement("//*[local-name()='a']", new QName("a"));
		assertGetElement("//*[local-name()='a' and namespace-uri()='b']", new QName("b", "a"));
	}

}
