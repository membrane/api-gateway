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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.Response.*;
import com.predic8.membrane.core.transport.http.*;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_FOR;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.util.Util.splitStringByComma;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.emptyList;
import static java.util.Locale.US;
import static org.apache.commons.text.StringEscapeUtils.*;

public class HttpUtil {

	private static final DateFormat GMT_DATE_FORMAT = createGMTDateFormat();
	private final static int MAX_LINE_LENGTH;

	static {
		String maxLineLength = System.getProperty("membrane.core.http.body.maxlinelength");
		MAX_LINE_LENGTH = maxLineLength == null ? 8092 : Integer.parseInt(maxLineLength);
	}

	/**
	 * Take out the last entry added by Membrane.
	 */
	public static List<String> getForwardedForList(Exchange exc) {
		String normalizedHeader = exc.getRequest().getHeader().getNormalizedValue(X_FORWARDED_FOR);
		if (normalizedHeader == null) return emptyList();
		List<String> xForwardedFor = new ArrayList<>(splitStringByComma(
				normalizedHeader
        ).stream().map(String::trim).toList());
		int addrIdx = xForwardedFor.lastIndexOf(exc.getRemoteAddrIp());
		if (!xForwardedFor.isEmpty() && addrIdx > -1) {
			xForwardedFor.remove(addrIdx);
		}
		return xForwardedFor;
	}

	/*
	 * @TODO Rewrite with DateTime
	 */
	public static DateFormat createGMTDateFormat() {
		SimpleDateFormat gmtDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", US);
		gmtDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return gmtDateFormat;
	}

	public static String readLine(InputStream in) throws IOException {

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
		response.setHeader(createHeaders(TEXT_HTML_UTF8));
		response.setBodyContent(getHTMLErrorBody(message, comment).getBytes(UTF_8));
		return response;
	}

	private static String getHTMLErrorBody(String text, String comment) {
		@SuppressWarnings("StringBufferReplaceableByString")
		StringBuilder buf = new StringBuilder(256);

		buf.append("""
				<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" \r
				  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">\r
				<html xmlns="http://www.w3.org/1999/xhtml">\r
				<head>\r
				<title>Internal Server Error</title>\r
				<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />\r
				<style><!--\r
				body { font-family:sans-serif; } \r
				.footer { margin-top:20pt; color:#AAAAAA; padding:1em 0em; font-size:10pt; }\r
				.footer a { color:#AAAAAA; }\r
				.footer a:hover { color:#000000; }\r
				--></style></head>\r
				<body><h1>Internal Server Error</h1>""");
		buf.append("<hr/>");
		buf.append("<p>While processing your request, the following error was detected. ");
		buf.append(comment);
		buf.append("</p>\r\n");
		buf.append("<pre id=\"msg\">");
		buf.append(escapeHtml4(text));
		buf.append("</pre>");
		buf.append("<p class=\"footer\">");
		buf.append(HTML_FOOTER);
		buf.append("</p>");
		buf.append("</body>");
		return buf.toString();
	}

	public static Header createHeaders(String contentType, String... headers) {
		Header header = new Header();
		if (contentType != null ) header.setContentType(contentType);
		synchronized (GMT_DATE_FORMAT) {
			header.add("Date", GMT_DATE_FORMAT.format(new Date()));
		}
		header.add("Server", PRODUCT_NAME);
		header.add("Connection", Header.CLOSE);
		for (int i = 0; i<headers.length; i+=2) {
			header.add(headers[i],headers[i+1]);
		}
		return header;
	}

	/**
	 * If there is no path like api.predic8.de it will return /
	 *
	 * @param dest URL e.g. http://predic8.de/foo?name=bar
	 * @return Path and query string without protocol and host e.g. /foo?name=bar
	 * @throws MalformedURLException
	 */
	public static String getPathAndQueryString(String dest) throws MalformedURLException {
		URL url = new URL(dest);
		String uri = url.getPath();

		if(url.getPath().isEmpty()) {
			uri = "/";
		}

		if (url.getQuery() != null) {
			return uri + "?" + url.getQuery();
		}
		return uri;
	}

	public static boolean isAbsoluteURI(String uri) {
		uri = uri.toLowerCase();
		return uri.startsWith("http://") || uri.startsWith("https://");
	}

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
			case 303 -> "See Other";
			case 304 -> "Not Modified";
			case 307 -> "Temporary Redirect";
			case 308 -> "Permanent Redirect";
			case 400 -> "Bad Request";
			case 401 -> "Unauthorized";
			case 403 -> "Forbidden";
			case 404 -> "Not Found";
			case 405 -> "Method Not Allowed";
			case 409 -> "Conflict";
			case 415 -> "Unsupported Mediatype";
			case 422 -> "Unprocessable Entity";
			case 500 -> "Internal Server Error";
			case 501 -> "Not Implemented";
			case 502 -> "Bad Gateway";
			case 503 -> "Service Unavailable";
			case 504 -> "Gateway Timeout";
			default -> "";
		};
	}

	public static boolean isIdempotent(String method) {
		return switch (method) {
			case METHOD_GET,  METHOD_PUT, METHOD_DELETE, METHOD_OPTIONS, METHOD_HEAD, METHOD_TRACE -> true;
			// POST, PATCH and CONNECT are not
			default -> false;
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
