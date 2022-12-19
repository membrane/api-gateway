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

import static com.predic8.membrane.core.util.URLParamUtil.*;
import static com.predic8.membrane.core.util.URLUtil.getHost;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

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
	public void testCreateQueryString() throws IOException {
		assertEquals("endpoint=http%3A%2F%2Fnode1.clustera&cluster=c1",
				createQueryString("endpoint", "http://node1.clustera",
						"cluster","c1"));

	}

	@Test
	public void testParseQueryString() throws IOException {
		assertEquals("http://node1.clustera", parseQueryString("endpoint=http%3A%2F%2Fnode1.clustera&cluster=c1").get("endpoint"));
		assertEquals("c1", parseQueryString("endpoint=http%3A%2F%2Fnode1.clustera&cluster=c1").get("cluster"));
	}

	@Test
	public void testParamsWithoutValueString() throws IOException {
		assertEquals("jim", parseQueryString("name=jim&male").get("name"));
		assertEquals("", parseQueryString("name=jim&male").get("male"));
		assertEquals("", parseQueryString("name=anna&age=").get("age"));
	}

	@Test
	public void testDecodePath() throws Exception{
		URI u = new URI(true,"/path/to%20my/resource");
		assertEquals("/path/to my/resource", u.getPath());
		assertEquals("/path/to%20my/resource",u.getRawPath());
	}
}
