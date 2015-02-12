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

package com.predic8.membrane.evaluation;

import java.io.*;

import org.junit.*;

import com.predic8.membrane.core.http.Request;

public class RequestPerformanceVersion2 {

	private int bufferSize;
	private byte[] buffer = new byte[1024];

	@Before
	public void setUp() throws Exception {
		fillByteArray();
	}


	@Test
	public void parseHeaderReadBufferFirst() throws Exception {
		long time = System.currentTimeMillis();
		for (int i = 0; i <= 1000000; i++) {
			parseHeaderReadBufferFirst(new Request(), getStream());
		}
		System.out.println("time read buffer first: "
				+ (System.currentTimeMillis() - time) / 1000.0);
	}

	@Test
	public void parseHeaderFromArray() throws Exception {
		long time = System.currentTimeMillis();
		for (int i = 0; i <= 1000000; i++) {
			parseHeaderFromArray(new Request(), buffer );
		}
		System.out.println("time from array: "
				+ (System.currentTimeMillis() - time) / 1000.0);
	}

	private InputStream getStream() {
		return new BufferedInputStream( new ByteArrayInputStream(buffer, 0, bufferSize));
	}

	private void fillByteArray() throws Exception {
		InputStream in = getClass().getClassLoader().getResourceAsStream(
				"request.txt");
		bufferSize = 0;
		int p = 0;
		while ((p = in.read(buffer, bufferSize, buffer.length - bufferSize)) != -1) {
			bufferSize += p;
		}
	}

	private void parseHeaderFromArray(Request req, byte[] buf)
			throws Exception {

		int s = 0;
		int e = 0;

		e = nextToken(buf, s, ' ');
		req.setMethod(new String(buf, s, e - s));

		s = ++e;
		e = nextToken(buf, s, ' ');
		req.setUri(new String(buf, s, e - s));

		s = ++e;
		nextToken(buf, s, '/');

		s = ++e;
		e = nextToken(buf, s, (char) 13);
		req.setVersion(new String(buf, s, e - s));

		while (true) {
			s = e + 2;
			e = nextToken(buf, s, ':');
			String k = new String(buf, s, e - s);
			if ("".equals(k))
				break;

			s = ++e;
			e = nextToken(buf, s, '\n');
			String v = new String(buf, s, e - s);

			req.getHeader().add(k, v);
		}

		// System.out.println(req.getStartLine());
		// System.out.println(req.getHeader());
	}

	private void parseHeaderReadBufferFirst(Request req, InputStream in)
			throws Exception {

		byte[] buf = new byte[1024];
		in.read(buf, 0, 1024);
		parseHeaderFromArray(req, buf);
	}

	private int nextToken(byte[] buf, int start, char d) throws Exception {
		for (int p = start; p < buf.length; p++) {

			int c = buf[p];

			// System.out.println("p: [p:"+p+",char:'"+(char)c + "',int:"+
			// (int)c+",d:'"+(int)d+"']");

			if (c == 13 || c == d) {
				return p;
			}
		}
		throw new RuntimeException("delemiter " + d
				+ " not found befor end of stream or buffer.");
	}

}