/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.tests.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;

/**
 * @author course
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class HttpUtilTest extends TestCase {

	private static String s1 = "foo"+ Constants.CRLF + "bar"+ Constants.CRLF + Constants.CRLF;
	private static InputStream is1;
	
	protected void setUp() throws Exception {
		is1 = new ByteArrayInputStream(s1.getBytes());
	}
	public void testReadLine() throws IOException, EndOfStreamException {
		String line = HttpUtil.readLine(is1);
		assertEquals("foo", line);
		line = HttpUtil.readLine(is1);
		assertEquals("bar", line);
		line = HttpUtil.readLine(is1);
		assertEquals("", line);
	}

}
