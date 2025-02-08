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
import static com.predic8.membrane.core.util.ByteUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class ByteUtilTest {

	private static final String message1 = "This is a test message";

	private static final String message2 = "This is a test message with carriage return and linefeed " + CRLF;

	private InputStream in1, in2;

	@BeforeEach
	public void setUp() {
		in1 = new ByteArrayInputStream(message1.getBytes());
		in2 = new ByteArrayInputStream(message2.getBytes());
	}

	@AfterEach
	public void tearDown() throws Exception {
		in1.close();
		in2.close();
	}

	@Test
	public void testReadByteArray1() throws IOException {
		assertArrayEquals(readByteArray(in1, message1.length()), message1.getBytes());
	}

	@Test
	public void testReadByteArray2() throws IOException {
		assertArrayEquals(readByteArray(in2, message2.length()), message2.getBytes());
	}
}
