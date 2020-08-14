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

public class RequestPerformanceVersion1 {

	private int bufferSize;
	private byte[] buffer = new byte[1024];


	@Before
	public void setUp() throws Exception {
		fillByteArray();
	}

	@Test
	public void parseHeaderPerLine() throws Exception {
		long time = System.currentTimeMillis();
		for (int i = 0; i <= 100000; i++) {
			new Request().read(getStream(), false);
		}
		System.out.println("time per line: " + (System.currentTimeMillis()-time)/60000.0);

		//System.out.println(req.getStartLine());
		//System.out.println(req.getHeader());
	}

	@Test
	public void parseHeaderPerCharWithBuf() throws Exception {
		long time = System.currentTimeMillis();
		for (int i = 0; i <= 100000; i++) {
			parseHeaderWithTokensFromBuffer(new Request(), getStream());
		}
		System.out.println("time per char with buf: " + (System.currentTimeMillis()-time)/60000.0);
	}

	@Test
	public void parseHeaderPerCharWithString() throws Exception {
		long time = System.currentTimeMillis();
		for (int i = 0; i <= 100000; i++) {
			parseHeaderWithStringTokens(new Request(), getStream());
		}
		System.out.println("time per char with string: " + (System.currentTimeMillis()-time)/60000.0);
	}

	private InputStream getStream() {
		return new BufferedInputStream( new ByteArrayInputStream(buffer, 0, bufferSize));
	}

	private void fillByteArray() throws Exception {
		InputStream in = getClass().getClassLoader().getResourceAsStream("request.txt");
		bufferSize = 0;
		int read = 0;
		while ( (read = in.read(buffer, bufferSize, buffer.length-bufferSize)) != -1 ) {
			bufferSize += read;
		}
	}

	private void parseHeaderWithTokensFromBuffer(Request req, InputStream reader) throws Exception {

		int[] buf = new int[100];
		int read = 0;

		read = nextToken(reader, buf, ' ');
		req.setMethod(new String(buf,0,read));

		read = nextToken(reader, buf, ' ');
		req.setUri(new String(buf,0,read));

		nextToken(reader, buf, '/');

		read = nextToken(reader, buf, '\n');
		req.setVersion(new String(buf,0,read));

		while (true) {
			read = nextToken(reader, buf, ':');
			String k = new String(buf,0,read);

			if (k.equals("")) break;

			read = nextToken(reader, buf, '\n');
			String v = new String(buf,0,read);

			req.getHeader().add(k,v);
		}

		//System.out.println(req.getStartLine());
		//System.out.println(req.getHeader());
	}

	private void parseHeaderWithStringTokens(Request req, InputStream reader) throws Exception {

		req.setMethod(nextStringToken(reader, ' '));

		req.setUri(nextStringToken(reader, ' '));

		nextStringToken(reader, '/');

		req.setVersion(nextStringToken(reader, '\n'));

		while (true) {
			String k = nextStringToken(reader, ':');

			if (k.equals("")) break;

			String v = nextStringToken(reader, '\n');

			req.getHeader().add(k,v);
		}

		//System.out.println(req.getStartLine());
		//System.out.println(req.getHeader());
	}

	private String nextStringToken(InputStream in, char d) throws Exception {
		StringBuffer buf = new StringBuffer();
		int c = 0;

		while((c = in.read()) != -1) {

			//System.out.println("read: [read:"+read+",char:'"+c + "',int:"+ (int)c+",d:'"+(int)d+"']");

			if (c == 13) {
				in.read();
				return buf.toString();
			}
			if (c == d ) return buf.toString();

			buf.append((char)c);
		}
		throw new RuntimeException("delemiter " + d +" not found befor end of stream or buffer.");
	}

	private int nextToken(InputStream in, int[] buf, char d) throws Exception {
		for (int read = 0; read < buf.length; read++) {

			int c = in.read();

			//System.out.println("read: [read:"+read+",char:'"+c + "',int:"+ (int)c+",d:'"+(int)d+"']");

			if (c == 13) {
				in.read();
				return read;
			}
			if (c == d ) return read;

			buf[read] = c;
		}
		throw new RuntimeException("delemiter " + d +" not found befor end of stream or buffer.");
	}

}