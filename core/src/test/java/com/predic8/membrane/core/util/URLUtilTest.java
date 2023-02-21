/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */


package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static com.predic8.membrane.core.util.URLUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class URLUtilTest {

	@Test
	public void testHost() {
		assertEquals(getHost("service:a"), "a");
		assertEquals(getHost("service://a"), "a");
		assertEquals(getHost("a"), "a");
		assertEquals(getHost("a/b"), "a");
		assertEquals(getHost("service:a/b"), "a");
		assertEquals(getHost("service://a/b"), "a");
	}

	@Test
	public void testCreateQueryString() {
		assertEquals("endpoint=http%3A%2F%2Fnode1.clustera&cluster=c1",
				createQueryString("endpoint", "http://node1.clustera",
						"cluster","c1"));

	}

	@Test
	public void testParseQueryString() {
		assertEquals("http://node1.clustera", parseQueryString("endpoint=http%3A%2F%2Fnode1.clustera&cluster=c1", ERROR).get("endpoint"));
		assertEquals("c1", parseQueryString("endpoint=http%3A%2F%2Fnode1.clustera&cluster=c1", ERROR).get("cluster"));
	}

	@Test
	public void testParamsWithoutValueString() {
		assertEquals("jim", parseQueryString("name=jim&male", ERROR).get("name"));
		assertEquals("", parseQueryString("name=jim&male", ERROR).get("male"));
		assertEquals("", parseQueryString("name=anna&age=", ERROR).get("age"));
	}

	@Test
	public void testDecodePath() throws Exception{
		URI u = new URI(true,"/path/to%20my/resource");
		assertEquals("/path/to my/resource", u.getPath());
		assertEquals("/path/to%20my/resource",u.getRawPath());
	}

    @Test
    void getPortFromURLTest() throws MalformedURLException {
		assertEquals(2000, getPortFromURL(new URL("http://localhost:2000")));
		assertEquals(80, getPortFromURL(new URL("http://localhost")));
		assertEquals(443, getPortFromURL(new URL("https://api.predic8.de")));
    }
}
