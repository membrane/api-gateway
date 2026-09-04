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

import static com.predic8.membrane.annot.Constants.*;
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

	/**
	 * A stream that ends before the requested number of bytes has been delivered must be reported.
	 * Returning the pre-allocated buffer makes its NUL padding indistinguishable from real content,
	 * so a truncated message body is accepted as complete.
	 * See <a href="https://github.com/membrane/api-gateway/issues/3193">#3193</a>.
	 */
	@Test
	void readByteArrayThrowsWhenTheStreamEndsEarly() {
		assertThrows(EOFException.class, () -> readByteArray(new ByteArrayInputStream("part".getBytes()), 7));
	}

	/**
	 * The full-length read must keep working when it takes several read() calls to get there -
	 * a short read is not the same as the end of the stream.
	 */
	@Test
	void readByteArrayAccumulatesAcrossShortReads() throws IOException {
		assertArrayEquals(message1.getBytes(), readByteArray(oneByteAtATime(message1), message1.length()));
	}

	private static InputStream oneByteAtATime(String content) {
		return new FilterInputStream(new ByteArrayInputStream(content.getBytes())) {
			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return super.read(b, off, 1); // force the caller to loop
			}
		};
	}
}
