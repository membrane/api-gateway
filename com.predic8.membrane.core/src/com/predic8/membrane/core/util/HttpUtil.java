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

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.ErrorReadingStartLineException;

public class HttpUtil {

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
		ErrorReadingStartLineException exc = new ErrorReadingStartLineException();
		exc.setStartLine(line.toString());
		throw exc;
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
		response.setStatusMessage("Internal Server Error");

		response.setHeader(createHeader("text/html;charset=utf-8"));

		response.setBody(new Body("<html>" + message + "</html>"));
		return response;
	}

	public static Response createNotFoundResponse() {
		Response response = new Response();
		response.setStatusCode(404);
		response.setStatusMessage("Not Found");

		response.setHeader(createHeader("text/html;charset=utf-8"));

		response.setBody(new Body("<html><head><title>Page Not Found</title></head><body>" + "The requested page could't be found!" + "</body></html>"));
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
			return 80;
		}
		return url.getPort();
	}

}