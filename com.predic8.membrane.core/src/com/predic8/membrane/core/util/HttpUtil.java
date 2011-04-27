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
import java.net.MalformedURLException;
import java.net.URL;
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
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Body;

public class HttpUtil {

	private static Log log = LogFactory.getLog(HttpUtil.class.getName());

	public static final SimpleDateFormat GMT_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

	static {
		GMT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	public static String readLine(InputStream in) throws IOException, EndOfStreamException {

		StringBuffer line = new StringBuffer();
		int b;

		do {
			b = in.read(); // log.debug(b);
			if (b == -1) {
				// return line.toString();
				throw new EndOfStreamException("read byte -1: " + line);
			}

			if (b == 13) {
				b = in.read();
				if (b == 10) {
					return line.toString();
				}
				return line.toString();
			}

			if (b == 10) {
				return line.toString();
			}
			line.append((char) b);
		} while (b >= 0);
		throw new IOException("File ends before line is complete");
	}

	public static int readChunkSize(InputStream in) throws IOException {
		StringBuffer buffer = new StringBuffer();

		int c = 0;
		while ((c = in.read()) != -1) {
			if (c == 0xd) {
				c = in.read();
				if (c != 10) {
					throw new IllegalStateException("input stream contains invalid data");
				}
				break;
			} else {
				if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')) {
					buffer.append((char) c);
				}
			}
		}
		String str = buffer.toString();
		str.trim();
		int size = 0;
		if (str.length() > 0) {
			try {
				size = Integer.parseInt(str, 16);
			} catch (NumberFormatException nfe) {
				log.debug("failed to parse: " + str);
				nfe.printStackTrace();
			}
		}

		return size;
	}

	public static Response createErrorResponse(String message) {
		Response response = new Response();
		response.setStatusCode(500);
		response.setStatusMessage(message);
		
		response.setHeader(createHeader("text/html;charset=utf-8"));

		response.setBody(new Body("<html>" + message + "</html>"));
		return response;
	}

	public static Response createSOAPFaultResponse(String message) {
		Response response = new Response();
		response.setStatusCode(500);
		
		response.setHeader(createHeader("text/xml;charset=utf-8"));
		
		Body body = new Body(getFaultSOAPBody(message));
		
		response.setBody(body);
		return response;
	}
	
	private static String getFaultSOAPBody(String text) {
		StringBuffer buf = new StringBuffer();
		
		buf.append("<soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/'>");
		buf.append(Constants.CRLF);
		buf.append("<soapenv:Body>");
		buf.append(Constants.CRLF);
		buf.append("<soapenv:Fault>");
		
		buf.append(Constants.CRLF);
		
		buf.append("<faultcode>soapenv:Server</faultcode>");
		buf.append(Constants.CRLF);
		buf.append("<faultstring>" + "Message validation failed!" + "</faultstring>");
		buf.append(Constants.CRLF);
		
		buf.append("<detail>" + text + "</detail>");
		
		buf.append(Constants.CRLF);
		buf.append("</soapenv:Fault>");
		buf.append(Constants.CRLF);
		buf.append("</soapenv:Body>");
		buf.append(Constants.CRLF);
		buf.append("</soapenv:Envelope>");
		return buf.toString();
	}
	
	private static Header createHeader(String contentType) {
		Header header = new Header();
		header.setContentType(contentType);
		header.add("Date", HttpUtil.GMT_DATE_FORMAT.format(new Date()));
		header.add("Server", "Membrane-Monitor " + Constants.VERSION);
		header.add("Connection", "close");
		return header;
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

	public static String getHostName(String destination) throws MalformedURLException {
		return new URL(destination).getHost();
	}

	public static String getPathAndQueryString(String dest) throws MalformedURLException {
		URL url = new URL(dest);
		
		String uri = url.getPath();
		if (url.getQuery() != null) {
			return uri + "?" + url.getQuery();
		}
		return uri;
	}
	
	public static int getPort(String hostAndPort) {
		return Integer.parseInt(hostAndPort.split(":")[1]);
	}
	
	public static int getPort(URL url) throws MalformedURLException {
		if (url.getPort() == -1) {
			log.debug("URL Port is not set. Default target port 80 will be used.");
			return 80;
		}
		return url.getPort();
	}

	public static String getCredentials(String user, String password) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Basic ");
		byte[] base64UserPass = Base64.encodeBase64((user + ":" + password).getBytes());
		buffer.append(new String(base64UserPass));
		return buffer.toString();
	}

}