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

import com.predic8.membrane.core.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.predic8.membrane.core.util.HttpUtil.readLine;
import static org.junit.jupiter.api.Assertions.*;

public class HttpUtilTest {

	private static String s1 = "foo" + Constants.CRLF + "bar" + Constants.CRLF
			+ Constants.CRLF;
	private static InputStream is1;

	@BeforeEach
	public void setUp() throws Exception {
		is1 = new ByteArrayInputStream(s1.getBytes());
	}

	@Test
	public void testReadLine() throws IOException, EndOfStreamException {
		String line = readLine(is1);
		assertEquals("foo", line);
		line = readLine(is1);
		assertEquals("bar", line);
		line = readLine(is1);
		assertEquals("", line);
	}

	@Test
	public void testReadLineMessage() throws Exception {
		InputStream in = getClass().getClassLoader().getResourceAsStream("request-post.msg");
		String line = readLine(in);
		assertEquals("POST /operation/call HTTP/1.1", line);
	}

}
