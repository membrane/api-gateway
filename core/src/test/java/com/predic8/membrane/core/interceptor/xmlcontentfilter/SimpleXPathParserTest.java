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

import org.junit.Assert;

import org.junit.Test;

import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParser.ContainerNode;

public class SimpleXPathParserTest {

	SimpleXPathParser p = new SimpleXPathParser();

	@Test
	public void valid1() {
		Assert.assertNotNull(p.parse("//a"));
	}

	@Test
	public void valid2() {
		ContainerNode n = p.parse("//a[@b='c'] (: comment (: nested comment :) :) and (//d)");
		Assert.assertNotNull(n);
		Assert.assertEquals(6, n.nodes.length);
	}

	@Test(expected = RuntimeException.class)
	public void invalid1() {
		p.parse("//a[");
	}

}
