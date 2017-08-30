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

import java.io.*;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.regex.*;

import javax.mail.internet.ContentType;
import javax.mail.internet.ParseException;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.cookie.Cookies;
import com.predic8.membrane.core.http.cookie.MimeHeaders;
import com.predic8.membrane.core.http.cookie.ServerCookie;
import com.predic8.membrane.core.util.*;

/**
 * The headers of a HTTP message.
 */
public class Header {

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

	public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

	public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

	public static final String ORIGIN = "Origin";

	public static final String X_HTTP_METHOD_OVERRIDE = "X-HTTP-Method-Override";

	// Header field values

	public static final String CHUNKED = "chunked";

	public static final String TIMEOUT = "timeout";
	public static final String MAX = "max";

	public static final String CLOSE = "close";

	private static final Pattern mediaTypePattern = Pattern.compile("(.+)/([^;]+)(;.*)?");
	private static final Pattern parameterPattern = Pattern.compile("(.+)=\"?([^\"]+)\"?");

	private static final Pattern timeoutPattern = Pattern.compile("timeout\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern maxPattern = Pattern.compile("max\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

	private static final Logger log = LoggerFactory.getLogger(Header.class.getName());



    private final ArrayList<HeaderField> fields = new ArrayList<HeaderField>();

	public Header() {
	}

	public Header(InputStream in) throws IOException, EndOfStreamException {
		String line;

		while ((line = HttpUtil.readLine(in)).length() > 0) {
			try {

				add(new HeaderField(line));
			} catch (StringIndexOutOfBoundsException sie) {
				log.error("Header read line that caused problems: " + line);
			}
		}
	}

	public Header(String header) throws IOException, EndOfStreamException {
		for (String line : header.split("\r?\n"))
			if (line.length() > 0)
				add(new HeaderField(line));
	}

	public Header(Header header) {
		for (HeaderField field : header.fields) {
			fields.add(new HeaderField(field));
		}
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
		List<HeaderField> deleteValues = new ArrayList<HeaderField>();
		for (HeaderField field : fields) {
			if (field.getHeaderName().equals(name))
				deleteValues.add(field);
		}
		fields.removeAll(deleteValues);
	}

	public List<HeaderField> getValues(HeaderName headerName) {
		List<HeaderField> res = new ArrayList<HeaderField>();
		for (HeaderField headerField : fields) {
			if (headerField.getHeaderName().equals(headerName))
				res.add(headerField);
		}
		return res;
	}

	public String getFirstValue(String name) {
		for (HeaderField field : fields) {
			if (field.getHeaderName().equals(name))
				return field.getValue();
		}
		return null;
	}

	public HeaderField[] getAllHeaderFields() {
		return fields.toArray(new HeaderField[fields.size()]);
	}

	/**
	 * Since {@link HttpUtil#readLine(InputStream)} assembles the String byte-by-byte
	 * converting it to char-by-char, we use ISO-8859-1 for output here.
	 */
	public void write(OutputStream out) throws IOException {
		StringBuilder buffer = new StringBuilder();
		for (HeaderField field : fields) {
			String name = field.getHeaderName().toString();
			String value = field.getValue();
			buffer.append(name).append(": ").append(value)
			.append(Constants.CRLF);
		}
		out.write(buffer.toString().getBytes(Constants.ISO_8859_1_CHARSET));
	}

	public void setValue(String name, String value) {
		boolean found = false;
		for (int i = 0; i < fields.size(); i++) {
			if (fields.get(i).getHeaderName().equals(name)) {
				if (found) {
					fields.set(i, fields.get(fields.size()-1));
					fields.remove(fields.size()-1);
					i--;
				} else {
					fields.get(i).setValue(value);
					found = true;
				}
			}
		}
		if (found)
			return;
		HeaderField newField = new HeaderField(name, value);
		fields.add(newField);
		return;
	}

	public void setHost(String value) {
		setValue(HOST, value);
	}

	public void setContentLength(long length) {
		setValue(CONTENT_LENGTH, "" + length);
	}

	public void setProxyAutorization(String value) {
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

	/**
	 * @return An object describing the value of the "Content-Type" HTTP header.
	 * 	Null, if the header is not present.
	 * @throws ParseException if the value of the header could not be parsed.
	 */
	public ContentType getContentTypeObject() throws ParseException {
		String contentType = getContentType();
		return contentType == null ? null : new ContentType(contentType);
	}

	public void setContentType(String value) {
		setValue(CONTENT_TYPE, value);
	}

	public String getSOAPAction() {
		return getFirstValue(SOAP_ACTION);
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

	public void setProxyConnection(String connection) {
		add(PROXY_CONNECTION, connection);
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
		StringBuilder res = new StringBuilder();
		for (HeaderField field : fields) {
			res.append(field.toString());
		}
		return res.toString();
	}

	public void setAuthorization(String user, String password)
			throws UnsupportedEncodingException {

		String value = "Basic "
				+ new String(Base64.encodeBase64((user + ":" + password)
						.getBytes("UTF-8")), "UTF-8");

		setValue("Authorization", value);
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

	public String getUserAgent() {
		return getFirstValue(USER_AGENT);
	}

	// TODO header value is a complex unit
	public String getCharset() {
		if (getContentType() == null)
			return Constants.UTF_8;

		String charset = getMediaTypeParameters().get("charset");
		if (charset == null)
			return Constants.UTF_8;
		return charset;
	}

	private Map<String, String> getMediaTypeParameters() {
		Matcher m = mediaTypePattern.matcher(getContentType());
		m.matches();
		boolean logDebug = log.isDebugEnabled();
		if (logDebug) {
			log.debug("type: " + m.group(1));
			log.debug("subtype: " + m.group(2));
			log.debug("parameters: " + m.group(3));
		}

		Map<String, String> map = new HashMap<String, String>();
		if (m.group(3) == null)
			return map;

		for (String param : m.group(3).substring(1).split("\\s*;\\s*")) {
			if (logDebug)
				log.debug("parsing parameter: " + param);
			Matcher paramMat = parameterPattern.matcher(param);
			if (paramMat.matches()) {
				if (logDebug)
					log.debug("parameter: " + paramMat.group(1) + "="
							+ paramMat.group(2));
				map.put(paramMat.group(1).trim(), paramMat.group(2));
			} else {
				if (logDebug)
					log.debug("parameter did not match " + parameterPattern.toString());
			}
		}
		return map;
	}

	public void addCookieSession(String cookieName, String value) {
		add(SET_COOKIE, cookieName + "=" + value);
	}

	public String getFirstCookie(String cookieName) {
		Cookies c = new Cookies(new MimeHeaders(this));
		for (int i = 0; i < c.getCookieCount(); i++) {
			ServerCookie sc = c.getCookie(i);
			if (sc.getName().equals(cookieName))
				return sc.getValue().toString();
		}
		return null;
	}

	public int estimateHeapSize() {
		int size = 10;
		for (HeaderField hf : fields)
			size += 4 + hf.estimateHeapSize();
		return size;
	}

	/**
	 * Tries to determines the index of the best content type.
	 */
	public int getBestAcceptedType(MediaType[] supported) {
		String accept = getFirstValue(ACCEPT);
		if (accept == null)
			return -1;
		List<MediaType> m;
		try {
			m = MediaType.parseMediaTypes(accept);
		} catch (IllegalArgumentException e) {
			return -1;
		}
		MediaType.sortByQualityValue(m);
		for (MediaType t : m)
			for (int i = 0; i < supported.length; i++)
				if (t.includes(supported[i]))
					return i;
		return -1;
	}

	public static MediaType[] convertStringsToMediaType(String[] mediaTypes) {
		MediaType[] m = new MediaType[mediaTypes.length];
		for (int i = 0; i < mediaTypes.length; i++)
			m[i] = MediaType.parseMediaType(mediaTypes[i]);
		return m;
	}

	public int getNumberOf(String headerName) {
		int res = 0;
		for (HeaderField headerField : fields)
			if (headerField.getHeaderName().equals(headerName))
				res++;
		return res;
	}

	/**
	 * @param keepAliveHeaderValue the value of the "Keep-Alive" header, see http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Keep-Alive
	 * @param paramName either {@link #TIMEOUT} or {@link #MAX}.
	 * @return the extracted parameter value of the "Keep-Alive" header
	 */
	public static long parseKeepAliveHeader(String keepAliveHeaderValue, String paramName) {
		Pattern p;
		if (paramName == TIMEOUT) {
			p = timeoutPattern;
		} else if (paramName == MAX) {
			p = maxPattern;
		} else {
			throw new InvalidParameterException("paramName must be one of Header.TIMEOUT and .MAX .");
		}
		Matcher m = p.matcher(keepAliveHeaderValue);
		if (!m.find())
			return -1;
		return Long.parseLong(m.group(1));
	}

	public void clear() {
		fields.clear();
	}

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

		if (userAgent.length() == p)
			return -1;
		char c = userAgent.charAt(p++);
		if (c != ' ' && c != '/' && c != '_')
			return -1;

		int version = 0;
		while (userAgent.length() != p) {
			c = userAgent.charAt(p++);
			if (c < '0' || c > '9')
				break;
			version = version * 10 + (c - '0');
		}
		return version;
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

	public void setWwwAuthenticate(String params){
		setValue(WWW_AUTHENTICATE,params);
	}

	public String getWwwAuthenticate(){
		return getFirstValue(WWW_AUTHENTICATE);
	}

	public String getNormalizedValue(String headerName) {
		StringBuilder sb = new StringBuilder();
		for (HeaderField headerField : fields) {
			if (headerField.getHeaderName().equals(headerName)) {
				if (sb.length() > 0)
					sb.append(",");
				sb.append(headerField.getValue());
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}
}
