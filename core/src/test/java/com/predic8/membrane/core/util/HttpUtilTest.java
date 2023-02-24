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

import java.io.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.util.HttpUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class HttpUtilTest {

	private static final String s1 = "foo" + CRLF + "bar" + CRLF + CRLF;
	private static InputStream is1;

	@BeforeEach
	public void setUp() throws Exception {
		is1 = new ByteArrayInputStream(s1.getBytes());
	}

	@Test
	public void testReadLine() throws IOException, EndOfStreamException {
		assertEquals("foo", readLine(is1));
		assertEquals("bar", readLine(is1));
		assertEquals("", readLine(is1));
	}

	@SuppressWarnings("DataFlowIssue")
	@Test
	public void testReadLineMessage() throws Exception {
		assertEquals("POST /operation/call HTTP/1.1", readLine(getClass().getClassLoader().getResourceAsStream("request-post.msg")));
	}

    @Test
    void unescapedHtmlMessageTest() {
		assertEquals("<html><head><title>caption</title></head><body><h1>caption</h1><p>body</p></body></html>", unescapedHtmlMessage("caption","body"));
    }
}
