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

package com.predic8.membrane.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.web.util.HtmlUtils;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.ErrorReadingStartLineException;

public class HttpUtil {
	
	private static DateFormat GMT_DATE_FORMAT = createGMTDateFormat();

	public static DateFormat createGMTDateFormat() {
		SimpleDateFormat GMT_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		GMT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
		return GMT_DATE_FORMAT;
	}

	public static String readLine(InputStream in) throws IOException, EndOfStreamException {

		StringBuilder line = new StringBuilder(128);

		int b;
		while ((b = in.read()) != -1) {
			if (b == 13) {
				in.read();
				return line.toString();
			}

			line.append((char) b);
		}
		
		throw new ErrorReadingStartLineException(line.toString());
	}

	
	public static int readChunkSize(InputStream in) throws IOException {
		StringBuilder buffer = new StringBuilder();

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

	public static Response createNotFoundResponse() {
		Response response = new Response();
		response.setStatusCode(404);
		response.setStatusMessage("Not Found");

		response.setHeader(createHeaders(MimeType.TEXT_XML_UTF8));

		response.setBody(new Body((
				"<html><head><title>Page Not Found</title></head><body>" +
				"The requested page could't be found!" + 
				"</body></html>").getBytes(Constants.UTF_8_CHARSET)));
		return response;
	}

	public static Response createHTMLErrorResponse(String message, String comment) {
		Response response = Response.interalServerError().build();
		response.setHeader(createHeaders(MimeType.TEXT_HTML_UTF8));
		response.setBodyContent(getHTMLErrorBody(message, comment).getBytes(Constants.UTF_8_CHARSET));
		return response;
	}

	private static String getHTMLErrorBody(String text, String comment) {
		StringBuilder buf = new StringBuilder(256);

		buf.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \r\n" + 
				"  \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\r\n" + 
				"<html xmlns=\"http://www.w3.org/1999/xhtml\">\r\n" + 
				"<head>\r\n" + 
				"<title>Internal Server Error</title>\r\n" + 
				"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\r\n" +
				"<style><!--\r\n" +
				"body { font-family:sans-serif; } \r\n" +
				".footer { margin-top:20pt; color:#AAAAAA; padding:1em 0em; font-size:10pt; }\r\n" + 
				".footer a { color:#AAAAAA; text-decoration: none; }\r\n" + 
				".footer a:hover { text-decoration: underline; }\r\n" + 
				"--></style>" +
				"</head>\r\n" + 
				"<body><h1>Internal Server Error</h1>");
		buf.append("<hr/>");
		buf.append("<p>While processing your request, the following error was detected. ");
		buf.append(comment);
		buf.append("</p>\r\n");
		buf.append("<pre id=\"msg\">");
		buf.append(HtmlUtils.htmlEscape(text));
		buf.append("</pre>");
		buf.append("<p class=\"footer\">");
		buf.append(Constants.HTML_FOOTER);
		buf.append("</p>");
		buf.append("</body>");
		return buf.toString();
	}

	public static Response createSOAPValidationErrorResponse(String message) {
		Response response = new Response();
		response.setStatusCode(400);
		response.setStatusMessage("Bad request");

		response.setHeader(createHeaders(MimeType.TEXT_XML_UTF8));

		Body body = new Body(getFaultSOAPBody(message).getBytes(Constants.UTF_8_CHARSET));

		response.setBody(body);
		return response;
	}

	private static String getFaultSOAPBody(String text) {
		StringBuilder buf = new StringBuilder(256);

		buf.append("<soapenv:Envelope xmlns:soapenv=\"" + Constants.SOAP11_NS + "/\">");
		buf.append(Constants.CRLF);
		buf.append("<soapenv:Body>");
		buf.append(Constants.CRLF);
		buf.append("<soapenv:Fault>");

		buf.append(Constants.CRLF);

		buf.append("<faultcode>soapenv:Server</faultcode>");
		buf.append(Constants.CRLF);
		buf.append("<faultstring>" + "Message validation failed!" + "</faultstring>");
		buf.append(Constants.CRLF);

		buf.append("<detail>" + StringEscapeUtils.escapeXml(text) + "</detail>");

		buf.append(Constants.CRLF);
		buf.append("</soapenv:Fault>");
		buf.append(Constants.CRLF);
		buf.append("</soapenv:Body>");
		buf.append(Constants.CRLF);
		buf.append("</soapenv:Envelope>");
		return buf.toString();
	}

	public static Response createResponse(int code, String msg, String body, String contentType, String... headers) {
		Response res = new Response();
		res.setStatusCode(code);
		res.setStatusMessage(msg);
		res.setHeader(createHeaders(contentType, headers));

		if (body != null) res.setBody(new Body(body.getBytes(Constants.UTF_8_CHARSET)));
		return res;		
	}
	
	public static Header createHeaders(String contentType, String... headers) {
		Header header = new Header();
		if (contentType != null ) header.setContentType(contentType);
		synchronized (GMT_DATE_FORMAT) {
			header.add("Date", GMT_DATE_FORMAT.format(new Date()));
		}
		header.add("Server", Constants.PRODUCT_NAME + " " + Constants.VERSION + ". See http://membrane-soa.org");
		header.add("Connection", "close");
		for (int i = 0; i<headers.length; i+=2) {
			header.add(headers[i],headers[i+1]);
		}
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

	public static boolean isAbsoluteURI(String uri) {
		uri = uri.toLowerCase();
		return uri.startsWith("http://") || uri.startsWith("https://");
	}
}