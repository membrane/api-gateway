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
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.LineTooLongException;


import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.transport.http.EOFWhileReadingLineException;
import org.apache.commons.lang3.*;

import static com.predic8.membrane.core.Constants.CRLF;
import static org.apache.commons.lang3.StringEscapeUtils.*;

public class HttpUtil {

	private static final DateFormat GMT_DATE_FORMAT = createGMTDateFormat();
	private final static int MAX_LINE_LENGTH;

	static {
		String maxLineLength = System.getProperty("membrane.core.http.body.maxlinelength");
		MAX_LINE_LENGTH = maxLineLength == null ? 8092 : Integer.parseInt(maxLineLength);
	}

	public static DateFormat createGMTDateFormat() {
		SimpleDateFormat GMT_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		GMT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
		return GMT_DATE_FORMAT;
	}

	public static String readLine(InputStream in) throws IOException, EndOfStreamException {

		StringBuilder line = new StringBuilder(128);

		int b;
		int l = 0;
		while ((b = in.read()) != -1) {
			if (b == 13) {
				//noinspection ResultOfMethodCallIgnored
				in.read();
				return line.toString();
			}
			if (b == 10) {
				in.mark(2);
				if (in.read() != 13)
					in.reset();
				return line.toString();
			}

			line.append((char) b);
			if (++l == MAX_LINE_LENGTH)
				throw new LineTooLongException(line.toString());
		}

		throw new EOFWhileReadingLineException(line.toString());
	}

    public static Response setHTMLErrorResponse(ResponseBuilder responseBuilder, String message, String comment) {
		Response response = responseBuilder.build();
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
				".footer a { color:#AAAAAA; }\r\n" +
				".footer a:hover { color:#000000; }\r\n" +
				"--></style>" +
				"</head>\r\n" +
				"<body><h1>Internal Server Error</h1>");
		buf.append("<hr/>");
		buf.append("<p>While processing your request, the following error was detected. ");
		buf.append(comment);
		buf.append("</p>\r\n");
		buf.append("<pre id=\"msg\">");
		buf.append(escapeHtml4(text));
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

		response.setBodyContent(getFaultSOAPBody(message).getBytes(Constants.UTF_8_CHARSET));
		return response;
	}

	private static String getFaultSOAPBody(String text) {
		return getFaultSOAPBody("Message validation failed!", text);
	}

	public static String getFaultSOAPBody(String title, String text) {
		StringBuilder buf = new StringBuilder(256);

		buf.append("<soapenv:Envelope xmlns:soapenv=\"" + Constants.SOAP11_NS + "\">");
		buf.append(CRLF);
		buf.append("<soapenv:Body>");
		buf.append(CRLF);
		buf.append("<soapenv:Fault>");

		buf.append(CRLF);

		buf.append("<faultcode>soapenv:Server</faultcode>");
		buf.append(CRLF);
		buf.append("<faultstring>");
		buf.append(escapeXml11(title));
		buf.append("</faultstring>");
		buf.append(CRLF);

		buf.append("<detail>" + escapeXml11(text) + "</detail>");

		buf.append(CRLF);
		buf.append("</soapenv:Fault>");
		buf.append(CRLF);
		buf.append("</soapenv:Body>");
		buf.append(CRLF);
		buf.append("</soapenv:Envelope>");
		return buf.toString();
	}

	public static String getFaultSOAP12Body(String title, String text) {
		StringBuilder buf = new StringBuilder(256);

		buf.append("<soapenv:Envelope xmlns:soapenv=\"" + Constants.SOAP12_NS + "\">");
		buf.append(CRLF);
		buf.append("<soapenv:Body>");
		buf.append(CRLF);
		buf.append("<soapenv:Fault>");
		buf.append(CRLF);

		buf.append("<soapenv:Code>");
		buf.append(CRLF);
		buf.append("<soapenv:Value>soapenv:Receiver</soapenv:Value>");
		buf.append(CRLF);
		buf.append("</soapenv:Code>");
		buf.append(CRLF);


		buf.append("<soapenv:Reason><soapenv:Text xml:lang=\"en-US\">");
		buf.append(escapeXml11(title));
		buf.append("</soapenv:Text></soapenv:Reason>");
		buf.append(CRLF);

		buf.append("<soapenv:Detail><Text>" + escapeXml11(text) + "</Text></soapenv:Detail>");

		buf.append(CRLF);
		buf.append("</soapenv:Fault>");
		buf.append(CRLF);
		buf.append("</soapenv:Body>");
		buf.append(CRLF);
		buf.append("</soapenv:Envelope>");
		return buf.toString();
	}

	public static Response createResponse(int code, String msg, byte[] body, String contentType, String... headers) {
		Response res = new Response();
		res.setStatusCode(code);
		res.setStatusMessage(msg);
		res.setHeader(createHeaders(contentType, headers));

		if (body != null) res.setBodyContent(body);
		return res;
	}

	public static Header createHeaders(String contentType, String... headers) {
		Header header = new Header();
		if (contentType != null ) header.setContentType(contentType);
		synchronized (GMT_DATE_FORMAT) {
			header.add("Date", GMT_DATE_FORMAT.format(new Date()));
		}
		header.add("Server", Constants.PRODUCT_NAME + " " + Constants.VERSION + ". See http://membrane-soa.org");
		header.add("Connection", Header.CLOSE);
		for (int i = 0; i<headers.length; i+=2) {
			header.add(headers[i],headers[i+1]);
		}
		return header;
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
		int port = url.getPort();
		if (port == -1) {
			port = url.getDefaultPort();
			if (port == -1)
				port = 80;
		}
		return port;
	}

	public static boolean isAbsoluteURI(String uri) {
		uri = uri.toLowerCase();
		return uri.startsWith("http://") || uri.startsWith("https://");
	}

	/**
	 * @param code
	 * @return
	 */
	public static String getMessageForStatusCode(int code) {
		return switch (code) {
			case 100 -> "Continue";
			case 200 -> "Ok";
			case 201 -> "Created";
			case 202 -> "Accepted";
			case 204 -> "No Content";
			case 206 -> "Partial Content";
			case 301 -> "Moved Permanently";
			case 302 -> "Found";
			case 304 -> "Not Modified";
			case 307 -> "Temporary Redirect";
			case 308 -> "Permanent Redirect";
			case 400 -> "Bad Request";
			case 401 -> "Unauthorized";
			case 403 -> "Forbidden";
			case 404 -> "Not Found";
			case 405 -> "Method not Allowed";
			case 409 -> "Conflict";
			case 415 -> "Unsupported Mediatype";
			case 422 -> "Unprocessable Entity";
			case 500 -> "Internal Server Error";
			case 501 -> "Not Implemented";
			case 502 -> "Bad Gateway";
			default -> "";
		};
	}

    public static String unescapedHtmlMessage(String caption, String text) {
        return "<html><head><title>" + caption
                + "</title></head>" + "<body><h1>"
                + caption + "</h1><p>"
                + text + "</p></body></html>";
    }

    public static String htmlMessage(String caption, String text) {
        return unescapedHtmlMessage(
                escapeHtml4(caption),
                escapeHtml4(text));
    }
}