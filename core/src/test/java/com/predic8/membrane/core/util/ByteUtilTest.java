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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.zip.Deflater;

import static com.predic8.membrane.core.Constants.CRLF;
import static com.predic8.membrane.core.util.ByteUtil.getDecompressedData;
import static com.predic8.membrane.core.util.ByteUtil.readByteArray;
import static java.nio.charset.StandardCharsets.UTF_8;
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

	@Test
	void validDeflateBodyReturnsOnlyDecompressedBytes() throws IOException {
		// Regression: chunk.write() added HTTP chunk framing, e.g. "7\r\npayload\r\n".
		// Decoding must return only the original bytes, including for empty and multi-buffer bodies.
		for (String content : new String[]{"", "payload", "x".repeat(4096)}) {
			assertArrayEquals(content.getBytes(UTF_8), getDecompressedData(rawDeflate(content)));
		}
	}

	/**
	 * A truncated raw deflate body leaves {@link java.util.zip.Inflater} unfinished and needing
	 * input. ByteUtil must reject it instead of repeatedly calling inflate() with no progress.
	 */
	@Test
	void truncatedDeflateBodyDoesNotLoopForever() {
		byte[] compressed = rawDeflate("payload");
		byte[] truncated = Arrays.copyOf(compressed, compressed.length - 1);

		assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
				assertThrows(IOException.class, () -> getDecompressedData(truncated)));
	}

	private static byte[] rawDeflate(String value) {
		var deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
		try {
			deflater.setInput(value.getBytes(UTF_8));
			deflater.finish();
			byte[] compressed = new byte[128];
			int length = deflater.deflate(compressed);
			return Arrays.copyOf(compressed, length);
		} finally {
			deflater.end();
		}
	}
}
