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

package com.predic8.membrane.core.http;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.http.cookie.*;
import com.predic8.membrane.core.util.*;
import jakarta.mail.internet.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.*;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.HttpUtil.readLine;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.stream;
import static java.util.regex.Pattern.*;
import static org.apache.commons.codec.binary.Base64.*;

/**
 * The headers of an HTTP message.
 */
public class Header {

	private static final Logger log = LoggerFactory.getLogger(Header.class.getName());

	// Header field names

	public static final String TRANSFER_ENCODING = "Transfer-Encoding";

	public static final String CONTENT_ENCODING = "Content-Encoding";

	public static final String CONTENT_LENGTH = "Content-Length";

	public static final String CONTENT_TYPE = "Content-Type";

	public static final String CONNECTION = "Connection";

	public static final String PROXY_CONNECTION = "Proxy-Connection";

	public static final String HOST = "Host";

	public static final String EXPECT = "Expect";

	public static final String X_FORWARDED_FOR = "X-Forwarded-For";

	public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

	public static final String X_FORWARDED_HOST = "X-Forwarded-Host";

	public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";

	public static final String SOAP_ACTION = "SOAPAction";

	public static final String ACCEPT = "Accept";
	public static final String LOCATION = "Location";

	public static final String AUTHORIZATION = "Authorization";

	public static final String SET_COOKIE = "Set-Cookie";

	public static final String COOKIE = "Cookie";

	public static final String DESTINATION = "Destination";

	public static final String VALIDATION_ERROR_SOURCE = "X-Validation-Error-Source";

	public static final String USER_AGENT = "User-Agent";

	public static final String X_REQUESTED_WITH = "X-Requested-With";

	public static final String EXPIRES = "Expires";

	public static final String KEEP_ALIVE = "Keep-Alive";

	public static final String SERVER = "Server";

	public static final String PRAGMA = "Pragma";

	public static final String CACHE_CONTROL = "Cache-Control";

	public static final String UPGRADE = "Upgrade";

	public static final String LAST_MODIFIED = "Last-Modified";

	public static final String IF_MODIFIED_SINCE = "If-Modified-Since";

	public static final String VARY = "VARY";

	public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

	public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

	public static final String ORIGIN = "Origin";

	public static final String X_HTTP_METHOD_OVERRIDE = "X-HTTP-Method-Override";

	public static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";

	public static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";

	/**
	 * Please note that this is a relic from RFC7540 and has been removed in RFC9113. It is present for backward
	 * compatibility (i.e. for Java's internal HTTP client).
	 */
	public static final String HTTP2_SETTINGS = "HTTP2-Settings";

	// Header field values
	public static final String CHUNKED = "chunked";

	public static final String TIMEOUT = "timeout";
	public static final String MAX = "max";

	public static final String CLOSE = "close";

	private static final Pattern timeoutPattern = compile("timeout\\s*=\\s*(\\d+)", CASE_INSENSITIVE);
	private static final Pattern maxPattern = compile("max\\s*=\\s*(\\d+)", CASE_INSENSITIVE);

	private final ArrayList<HeaderField> fields = new ArrayList<>();

	public Header() {
	}

	public Header(InputStream in) throws IOException, EndOfStreamException {
		String line;
		while (!(line = readLine(in)).isEmpty()) {
			try {
				add(new HeaderField(line));
			} catch (StringIndexOutOfBoundsException sie) {
                log.error("Header read line that caused problems: {}", line);
			}
		}
	}

	public Header(String header) throws IOException, EndOfStreamException {
		this(
				stream(header.split("\r?\n"))
						.filter(s -> !s.isEmpty())
						.map(HeaderField::new)
						.toList()
		);
	}

	public Header(Header header) {
		this(header.fields.stream().map(HeaderField::new).toList());
	}

	public Header(List<HeaderField> fields) {
		this.fields.addAll(fields);
	}

	public void add(String key, String val) {
		fields.add(new HeaderField(key, val));
	}

	public void add(HeaderField field) {
		fields.add(field);
	}

	public void remove(HeaderField field) {
		fields.remove(field);
	}

	public void removeFields(String name) {
		List<HeaderField> deleteValues = fields.stream()
				.filter(f -> f.getHeaderName().hasName(name))
				.toList();
		fields.removeAll(deleteValues);
	}

	public List<HeaderField> getValues(HeaderName headerName) {
		return fields.stream()
				.filter(field -> field.getHeaderName().equals(headerName))
				.toList();
	}

	public String getFirstValue(String name) {
        return fields.stream()
				.filter(field -> field.getHeaderName().hasName(name))
				.findFirst()
				.map(HeaderField::getValue)
				.orElse(null);
	}

	public String getFirstValue(HeaderName name) {
		return getFirstValue(name.toString());
	}

	public HeaderField[] getAllHeaderFields() {
		return fields.toArray(new HeaderField[0]);
	}

	public boolean contains(String header) {
		return fields.stream()
				.anyMatch(headerField -> headerField.getHeaderName().hasName(header));
	}

	public boolean contains(HeaderName header) {
		return contains(header.toString());
	}

	/**
	 * Since {@link HttpUtil#readLine(InputStream)} assembles the String byte-by-byte
	 * converting it to char-by-char, we use ISO-8859-1 for output here.
	 */
	public void write(OutputStream out) throws IOException {
		byte[] bytes = fields.stream()
				.map(f -> "%s: %s%s".formatted(f.getHeaderName(), f.getValue(), Constants.CRLF))
				.collect(Collectors.joining()).getBytes(ISO_8859_1);
		out.write(bytes);
	}

	public void setValue(String name, String value) {
		boolean found = false;
		for (int i = 0; i < fields.size(); i++) {
			if (fields.get(i).getHeaderName().hasName(name)) {
				if (found) {
					fields.set(i, fields.getLast());
					fields.removeLast();
					i--;
				} else {
					fields.get(i).setValue(value);
					found = true;
				}
			}
		}
		if (found)
			return;
		fields.add(new HeaderField(name, value));
	}

	public void setHost(String value) {
		setValue(HOST, value);
	}

	public void setContentLength(long length) {
		setValue(CONTENT_LENGTH, "" + length);
	}

	public void setProxyAuthorization(String value) {
		setValue(PROXY_AUTHORIZATION, value);
	}

	public boolean isChunked() {
		return CHUNKED.equals(getFirstValue(TRANSFER_ENCODING));
	}

	public long getContentLength() {
		if (!hasContentLength())
			return -1;
		return Long.parseLong(getFirstValue(CONTENT_LENGTH));
	}

	public String getContentType() {
		return getFirstValue(CONTENT_TYPE);
	}

	public String getUserAgent() {
		return getFirstValue(USER_AGENT);
	}

	/**
	 * @return An object describing the value of the "Content-Type" HTTP header.
	 * 	Null, if the header is not present.
	 * @throws ParseException if the value of the header could not be parsed.
	 */
	public ContentType getContentTypeObject() throws ParseException {
		String contentType = getContentType();
		return contentType == null ? null : new ContentType(contentType);
	}

	public void setContentType(String type) {
		setValue(CONTENT_TYPE, type);
	}

	public void setLocation(String location) {
		setValue(LOCATION, location);
	}

	public String getLocation() {
		return getFirstValue(LOCATION);
	}

	public void setSOAPAction(String value) {
		setValue(SOAP_ACTION, value);
	}

	public String getAccept() {
		return getFirstValue(ACCEPT);
	}

	public void setAccept(String value) {
		add(ACCEPT, value);
	}

	public String getConnection() {
		return getFirstValue(CONNECTION);
	}

	public void setConnection(String connection) {
		setValue(CONNECTION, connection);
	}

	public String getProxyConnection() {
		return getFirstValue(PROXY_CONNECTION);
	}

	public boolean isProxyConnectionClose() {
		if (getProxyConnection() == null)
			return false;

		return CLOSE.equalsIgnoreCase(getProxyConnection());
	}

	public boolean isConnectionClose() {
		if (getConnection() == null)
			return false;

		return CLOSE.equalsIgnoreCase(getConnection());
	}

	public boolean hasContentLength() {
		return getFirstValue(CONTENT_LENGTH) != null;
	}

	public String getHost() {
		return getFirstValue(HOST);
	}

	public boolean is100ContinueExpected() {
		return "100-continue".equalsIgnoreCase(getFirstValue(EXPECT));
	}

	@Override
	public String toString() {
		return fields.stream()
				.map(HeaderField::toString)
				.collect(Collectors.joining());
	}

	public void setAuthorization(String user, String password) {
		setValue("Authorization", "Basic "
								  + new String(encodeBase64((user + ":" + password)
						.getBytes(UTF_8)), UTF_8));
	}

	public void setXForwardedFor(String value) {
		setValue(X_FORWARDED_FOR, value);
	}

	public String getXForwardedFor() {
		return getFirstValue(X_FORWARDED_FOR);
	}

	public void setXForwardedProto(String value) {
		setValue(X_FORWARDED_PROTO, value);
	}

	public String getXForwardedProto() {
		return getFirstValue(X_FORWARDED_PROTO);
	}

	public String getContentEncoding() {
		return getFirstValue(CONTENT_ENCODING);
	}

	public String getCharset() {
		if (getContentType() == null)
			return UTF_8.name();

		try {
			return new ContentType(getContentType()).getParameter("charset").toUpperCase();
		} catch (Exception e) {
			return UTF_8.name();
		}
	}

	public void addCookieSession(String cookieName, String value) {
		addRawCookieSession(cookieName + "=" + value);
	}

	public void addRawCookieSession(String cookie){
		add(SET_COOKIE, cookie);
	}

	public String getFirstCookie(String cookieName) {
		Cookies c = new Cookies(new MimeHeaders(this));
		for (int i = 0; i < c.getCookieCount(); i++) {
			ServerCookie sc = c.getCookie(i);
			if (sc.getName().toString().equals(cookieName))
				return sc.getValue().toString();
		}
		return null;
	}

	public int estimateHeapSize() {
		return 10 + fields.stream()
				.map(f -> 4 + f.estimateHeapSize())
				.reduce(0, Integer::sum);
	}

	public int getNumberOf(String headerName) {
		return (int) fields.stream()
				.filter(f -> f.getHeaderName().hasName(headerName))
				.count();
	}

	/**
	 * @param keepAliveHeaderValue the value of the <a href="http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Keep-Alive">Keep-Alive</a> header
	 * @param paramName either {@link #TIMEOUT} or {@link #MAX}.
	 * @return the extracted parameter value of the "Keep-Alive" header
	 */
	public static long parseKeepAliveHeader(String keepAliveHeaderValue, String paramName) {
		Pattern p = choosePattern(paramName);
		Matcher m = p.matcher(keepAliveHeaderValue);
		if (!m.find())
			return -1;
		return Long.parseLong(m.group(1));
	}

	private static @NotNull Pattern choosePattern(String paramName) {
		if (paramName.equals(TIMEOUT)) {
			return timeoutPattern;
		} else if (paramName.equals(MAX)) {
			return maxPattern;
		} else {
			throw new InvalidParameterException("paramName must be one of Header.TIMEOUT and .MAX .");
		}
	}

	public void clear() {
		fields.clear();
	}

	public void setNoCacheResponseHeaders() {
		setValue(EXPIRES, "Tue, 03 Jul 2001 06:00:00 GMT");
		setValue(CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
		add(CACHE_CONTROL, "post-check=0, pre-check=0");
		add(PRAGMA, "no-cache");
	}

	public String getAuthorization() {
		return getFirstValue(AUTHORIZATION);
	}

	public String getWwwAuthenticate(){
		return getFirstValue(WWW_AUTHENTICATE);
	}

	public String getNormalizedValue(String headerName) {
		var s = fields.stream()
				.filter(f -> f.getHeaderName().hasName(headerName))
				.map(HeaderField::getValue)
				.collect(Collectors.joining(","));
		return s.isEmpty() ? null : s;
	}

	public boolean isBinaryContentType() {
		return isBinary(getContentType());
	}

	public String getXForwardedHost() {
		return getFirstValue(X_FORWARDED_HOST);
	}

	public void setXForwardedHost(String xForwardedHostHeaderValue) {
		setValue(X_FORWARDED_HOST,xForwardedHostHeaderValue);
	}

	/**
	 * Method can be used from Groovy or Javascripts
	 */
	@SuppressWarnings("unused")
	public boolean isUserAgentSupportsSNI() {
		// a mostly conservative approximation of http://en.wikipedia.org/wiki/Server_Name_Indication#Support
		String ua = getUserAgent();
		if (ua == null)
			return false;
		if (getBrowserVersion(ua, "Firefox") >= 2)
			return true;
		if (getBrowserVersion(ua, "Opera") >= 8)
			return true;
		if (getBrowserVersion(ua, "Safari") >= 522)
			return getBrowserVersion(ua, "Windows NT") >= 6 || getBrowserVersion(ua, "Mac OS X 10") >= 6;
		if (getBrowserVersion(ua, "MSIE") >= 7 || getBrowserVersion(ua, "Trident") >= 5)
			return getBrowserVersion(ua, "Windows NT") >= 6;
		if (getBrowserVersion(ua, "Chrome") > 0) {
			int windows = getBrowserVersion(ua, "Windows NT");
			return windows >= 6 || windows == -1;
		}
		return false;
	}

	private int getBrowserVersion(String userAgent, String browserID) {
		int p = userAgent.indexOf(browserID);
		p += browserID.length();
		if (p >= userAgent.length())
			return -1;
		char c = userAgent.charAt(p++);
		if (c != ' ' && c != '/' && c != '_')
			return -1;
		int version = 0;
		while (p < userAgent.length()) {
			c = userAgent.charAt(p++);
			if (c < '0' || c > '9')
				break;
			version = version * 10 + (c - '0');
		}
		return version;
	}

	public Stream<String> getSingleValues(String headerName) {
		return getValues(new HeaderName(headerName)).stream()
				.flatMap(v -> stream(v.getValue().split(",")))
				.map(String::trim);
	}

	public void keepOnly(String headerName, Predicate<String> valueFilter) {
		List<String> valuesToKeep = getSingleValues(headerName).filter(valueFilter).toList();
		removeFields(headerName);
		valuesToKeep.forEach(value -> add(headerName, value));
	}

	public String getUpgradeProtocol() {
		if (getSingleValues(CONNECTION).noneMatch(v -> v.equalsIgnoreCase(UPGRADE)))
			return null;
		return getFirstValue(UPGRADE);
	}

}
