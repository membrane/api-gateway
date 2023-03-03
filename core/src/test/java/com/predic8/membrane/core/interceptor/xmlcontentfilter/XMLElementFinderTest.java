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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class XMLElementFinderTest {
	private final static String DOC = "<a xmlns:s=\"space-s\" xmlns=\"space-default\"><b /><s:c></a>";

	private void testIt(String doc, boolean isExpectedToContainOneOf, QName... names) {
		List<QName> names2 = new ArrayList<>();
		for (QName name : names)
			names2.add(name);
		assertEquals(isExpectedToContainOneOf,
				new XMLElementFinder(names2).matches(new ByteArrayInputStream(doc.getBytes())));
	}

	@Test
	public void doit() {
		testIt(DOC, true, new QName("a"));
		testIt(DOC, true, new QName("space-default", "a"));
		testIt(DOC, false, new QName("space-other", "a"));

		testIt(DOC, true, new QName("b"));
		testIt(DOC, true, new QName("space-default", "b"));
		testIt(DOC, false, new QName("space-other", "b"));

		testIt(DOC, true, new QName("c"));
		testIt(DOC, true, new QName("space-s", "c"));
		testIt(DOC, false, new QName("space-other", "c"));

		testIt(DOC, false, new QName("d"));
		testIt(DOC, false, new QName("space-s", "d"));
		testIt(DOC, false, new QName("space-other", "d"));
	}

}
