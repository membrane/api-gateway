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

package com.predic8.membrane.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;

public class HttpUtil {

	private static Log log = LogFactory.getLog(HttpUtil.class.getName());

	public static final SimpleDateFormat GMT_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

	static {
		GMT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	public static String readLine(InputStream in) throws IOException, EndOfStreamException {

		StringBuffer line = new StringBuffer();
		int b;

		while ((b = in.read()) != -1) {

			if (b == 13) {
				in.read();
				return line.toString();
			}

			line.append((char) b);
		}
		throw new IOException("File ends before line is complete");
	}

	public static int readChunkSize(InputStream in) throws IOException {
		StringBuffer buffer = new StringBuffer();

		int c = 0;
		while ((c = in.read()) != -1) {
			if (c == 13) {
				c = in.read();
				break;
			}

			// ignore chunk extensions
			if (c == ';') {
				while ((c = in.read()) != 10)
					;
			}

			buffer.append((char) c);
		}
		
		return Integer.parseInt(buffer.toString().trim(), 16);
	}

	public static Response createErrorResponse(String message) {
		Response response = new Response();
		response.setStatusCode(500);
		response.setStatusMessage(message);
		Header header = new Header();
		header.setContentType("text/html;charset=utf-8");
		header.add("Date", HttpUtil.GMT_DATE_FORMAT.format(new Date()));
		header.add("Server", "Membrane-Monitor " + Constants.VERSION);
		header.add("Connection", "close");

		response.setHeader(header);

		response.setBody(new Body("<html>" + message + "</html>"));
		return response;
	}

	public static List<Chunk> readChunks(InputStream in) throws IOException {
		List<Chunk> chunks = new ArrayList<Chunk>();
		int chunkSize;
		while ((chunkSize = readChunkSize(in)) > 0) {
			chunks.add(new Chunk(ByteUtil.readByteArray(in, chunkSize)));
			in.read(); // CR
			in.read(); // LF
		}
		in.read(); // CR
		in.read(); // LF
		return chunks;
	}

	public static String getHost(String hostAndPort) {
		return hostAndPort.split(":")[0];
	}

	public static int getPort(String hostAndPort) {
		return Integer.parseInt(hostAndPort.split(":")[1]);
	}

	public static String getCredentials(String user, String password) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Basic ");
		byte[] base64UserPass = Base64.encodeBase64((user + ":" + password).getBytes());
		buffer.append(new String(base64UserPass));
		return buffer.toString();
	}

}