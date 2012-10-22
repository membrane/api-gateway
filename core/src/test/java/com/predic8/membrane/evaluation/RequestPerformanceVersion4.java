/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.http.Request;

public class RequestPerformanceVersion4 {

	private int bufferSize;
	private byte[] buffer = new byte[1024];

	@Before
	public void setUp() throws Exception {
		fillByteArray();
	}

	@Test
	public void testParsing() throws Exception {
		Request request = new Request();
		parseHeaderReadBufferFirst(request, getStream());
		System.out.println(request.getStartLine());
		System.out.println(request.getHeader());
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
		return new BufferedInputStream(new ByteArrayInputStream(buffer,0,bufferSize));
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
		for (e = s; e < buf.length; e++) {
		
			if ( buf[e] == ' ') {
				break;
			}
		}

		req.setMethod(new String(buf, s, e - s));

		s = ++e;
		for (e = s; e < buf.length; e++) {
		
			if ( buf[e] == ' ') {
				break;
			}
		}
		req.setUri(new String(buf, s, e - s));

		s = ++e;
		for (e = s; e < buf.length; e++) {
		
			if ( buf[e] == '/') {
				break;
			}
		}
		s = ++e;
		for (e = s; e < buf.length; e++) {
		
			if ( buf[e] == 13 ) {
				break;
			}
		}
		req.setVersion(new String(buf, s, e - s));

		while (true) {
			s = e + 2;
			for (e = s; e < buf.length; e++) {
			
				if (buf[e] == 13 || buf[e] == ':') {
					break;
				}
			}
			String k = new String(buf, s, e - s);
			if ("".equals(k))
				break;

			s = ++e;
			for (e = s; e < buf.length; e++) {
			
				if (buf[e] == 13) {
					break;
				}
			}
			String v = new String(buf, s, e - s);

			req.getHeader().add(k, v);
		}
	}

	private void parseHeaderReadBufferFirst(Request req, InputStream in)
			throws Exception {

		byte[] buf = new byte[1024];
		in.read(buf, 0, 1024);
		parseHeaderFromArray(req, buf);
	}

}