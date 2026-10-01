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
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.transport.http.BufferedConnectionInputStream;
import com.predic8.membrane.core.transport.http.EOFWhileReadingLineException;
import com.predic8.membrane.core.transport.http.LineTooLongException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.predic8.membrane.annot.Constants.HTML_FOOTER;
import static com.predic8.membrane.annot.Constants.PRODUCT_NAME;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_FOR;
import static com.predic8.membrane.core.http.MimeType.TEXT_HTML_UTF8;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.util.Util.splitStringByComma;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Locale.US;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

public class HttpUtil {

	private static final DateFormat GMT_DATE_FORMAT = createGMTDateFormat();
	private final static int MAX_LINE_LENGTH;

	static {
		String maxLineLength = System.getProperty("membrane.core.http.body.maxlinelength");
		MAX_LINE_LENGTH = maxLineLength == null ? 8092 : Integer.parseInt(maxLineLength);
	}

	// Longer lines fall back to reading byte by byte. Equal to the default buffer size of the
	// connection streams, so marking never makes a BufferedInputStream grow its buffer.
	private static final int BULK_READ_LIMIT = 2048;

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

	/**
	 * Reads a line terminated by CR or LF and consumes its terminator: a CR always takes the byte
	 * after it along, an LF only a CR directly after it. Bytes are converted to chars as ISO-8859-1.
	 */
	public static String readLine(InputStream in) throws IOException {
		return readLine(in, MAX_LINE_LENGTH);
	}

	/**
	 * @param maxLineLength a line of this many chars or more throws {@link LineTooLongException};
	 *                      zero or less means no limit
	 */
	static String readLine(InputStream in, int maxLineLength) throws IOException {
		// Exact classes only: a subclass may override read() and behave differently.
		if (in.getClass() == BufferedConnectionInputStream.class || in.getClass() == ByteArrayInputStream.class) {
			StringBuilder start = new StringBuilder();
			String line = readLineInBulk(in, maxLineLength, start);
			if (line != null)
				return line;
			return readLineByteByByte(in, maxLineLength, start);
		}
		return readLineByteByByte(in, maxLineLength, new StringBuilder(128));
	}

	/**
	 * Reads ahead with bulk reads instead of one read() per byte, which takes the stream's lock
	 * every time, then rewinds and skips exactly the bytes
	 * {@link #readLineByteByByte(InputStream, int, StringBuilder)} would have consumed. A read never
	 * asks for more than the bytes buffered, or for a single byte if there are none: a
	 * {@link BufferedConnectionInputStream} asked for more keeps reading from the underlying stream
	 * as long as its available() is positive, which may block although the line is complete. So
	 * this only waits where reading byte by byte would wait too.
	 *
	 * @param start receives the bytes known to belong to the line if no line is returned
	 * @return the line, or <code>null</code> if the stream ends or the line does not end within
	 * {@link #BULK_READ_LIMIT} bytes. The stream is then positioned after the bytes appended to
	 * <code>start</code>, so reading byte by byte can go on from there without reading them again.
	 */
	static String readLineInBulk(InputStream in, int maxLineLength, StringBuilder start) throws IOException {
		int limit = maxLineLength > 0 ? Math.min(BULK_READ_LIMIT, maxLineLength) : BULK_READ_LIMIT;
		byte[] bytes = new byte[Math.min(256, limit)];
		in.mark(limit);
		int n = 0;
		int i = 0;
		while (n < limit) {
			if (n == bytes.length)
				bytes = Arrays.copyOf(bytes, Math.min(2 * bytes.length, limit));
			int want = bytes.length - n;
			if (in instanceof BufferedConnectionInputStream b)
				want = Math.min(want, Math.max(1, b.buffered()));
			int read = in.read(bytes, n, want);
			if (read == -1)
				break;
			n += read;
			// The last byte read waits for the next read: a CR or LF needs the byte after it to
			// decide what to consume.
			for (; i < n - 1; i++) {
				if (bytes[i] == 13 || bytes[i] == 10) {
					in.reset();
					in.skipNBytes(bytes[i] == 13 || bytes[i + 1] == 13 ? i + 2 : i + 1);
					return new String(bytes, 0, i, ISO_8859_1);
				}
			}
		}
		// The first i bytes are checked and contain no terminator. Fewer than maxLineLength, so
		// they cannot make the line too long.
		in.reset();
		in.skipNBytes(i);
		start.append(new String(bytes, 0, i, ISO_8859_1));
		return null;
	}

	/**
	 * @param line the start of the line, already consumed from the stream
	 */
	private static String readLineByteByByte(InputStream in, int maxLineLength, StringBuilder line) throws IOException {

		int b;
		int l = line.length();
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
			if (++l == maxLineLength)
				throw new LineTooLongException(line.toString());
		}

		throw new EOFWhileReadingLineException(line.toString());
	}

	/**
	 * Whether the character is an RFC 9110 &sect;5.6.2 tchar, the character a token such as a field
	 * name or a method is made of.
	 */
	public static boolean isTchar(int c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
			   || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
	}

	/**
	 * Whether the character is RFC 9110 &sect;5.6.3 optional whitespace: SP or HTAB.
	 */
	public static boolean isOptionalWhitespace(char c) {
		return c == ' ' || c == '\t';
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
			case 200 -> "OK";
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
			case 406 -> "Not Acceptable";
			case 407 -> "Proxy Authentication Required";
			case 408 -> "Request Timeout";
			case 409 -> "Conflict";
			case 412 -> "Precondition Failed";
			case 415 -> "Unsupported Mediatype";
			case 418 -> "I'm a Teapot";
			case 422 -> "Unprocessable Entity";
			case 423 -> "Locked";
			case 428 -> "Precondition Required";
			case 429 -> "Too Many Requests";
			case 500 -> "Internal Server Error";
			case 501 -> "Not Implemented";
			case 502 -> "Bad Gateway";
			case 503 -> "Service Unavailable";
			case 504 -> "Gateway Timeout";
			case 508 -> "Loop Detected";
			default -> defaultMessageForStatusCode(code);
		};
	}

	/**
	 * Reason phrase for a status code that has no entry in the table above,
	 * derived from its class so the status line is never left with an empty phrase.
	 */
	private static String defaultMessageForStatusCode(int code) {
		return switch (code / 100) {
			case 1 -> "Information";
			case 2 -> "Success";
			case 3 -> "Redirection";
			case 4 -> "Client Error";
			case 5 -> "Server Error";
			default -> "Unknown";
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
