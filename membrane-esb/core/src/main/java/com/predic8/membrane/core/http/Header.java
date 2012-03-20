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

package com.predic8.membrane.core.http;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.mail.internet.ContentType;
import javax.mail.internet.ParseException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.*;

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

	public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";

	public static final String SOAP_ACTION = "SOAPAction";

	public static final String ACCEPT = "Accept";

	public static final String LOCATION = "Location";

	// Header field values

	public static final String CHUNKED = "chunked";

	private static final Pattern mediaTypePattern = Pattern.compile("(.+)/([^;]+)(;.*)?");
	private static final Pattern parameterPattern = Pattern.compile("(.+)=\"?([^\"]+)\"?");

	private static final Log log = LogFactory.getLog(Header.class.getName());

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
		StringBuffer buffer = new StringBuffer();
		for (HeaderField field : fields) {
			String name = field.getHeaderName().toString();
			String value = field.getValue();
			buffer.append(name).append(": ").append(value)
					.append(Constants.CRLF);
		}
		out.write(buffer.toString().getBytes(Constants.ISO_8859_1_CHARSET));
	}

	public HeaderField setValue(String name, String value) {
		for (HeaderField field : fields) {
			if (field.getHeaderName().equals(name)) {
				field.setValue(value);
				return field;
			}
		}
		HeaderField newField = new HeaderField(name, value);
		fields.add(newField);
		return newField;
	}

	public void setHost(String value) {
		setValue(HOST, value);
	}

	public void setContentLength(int length) {
		setValue(CONTENT_LENGTH, "" + length);
	}

	public void setProxyAutorization(String value) {
		setValue(PROXY_AUTHORIZATION, value);
	}

	public boolean isChunked() {
		return CHUNKED.equals(getFirstValue(TRANSFER_ENCODING));
	}

	public int getContentLength() {
		if (!hasContentLength())
			return -1;
		return Integer.parseInt(getFirstValue(CONTENT_LENGTH));
	}

	public String getContentType() {
		return getFirstValue(CONTENT_TYPE);
	}
	
	public ContentType getContentTypeObject() throws ParseException {
		String contentType = getContentType();
		return contentType == null ? null : new ContentType(contentType);
	}

	public void setContentType(String value) {
		add(CONTENT_TYPE, value);
	}

	public String getSOAPAction() {
		return getFirstValue(SOAP_ACTION);
	}

	public void setSOAPAction(String value) {
		add(SOAP_ACTION, value);
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
		add(CONNECTION, connection);
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

		return "close".equalsIgnoreCase(getProxyConnection());
	}

	public boolean isConnectionClose() {
		if (getConnection() == null)
			return false;

		return "close".equalsIgnoreCase(getConnection());
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
		StringBuffer res = new StringBuffer();
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

		add("Authorization", value);
	}

	public void setXForwardedFor(String value) {
		add(X_FORWARDED_FOR, value);
	}

	public String getXForwardedFor() {
		return getFirstValue(X_FORWARDED_FOR);
	}

	public String getContentEncoding() {
		return getFirstValue(CONTENT_ENCODING);
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
		log.debug("type: " + m.group(1));
		log.debug("subtype: " + m.group(2));
		log.debug("parameters: " + m.group(3));

		Map<String, String> map = new HashMap<String, String>();
		if (m.group(3) == null)
			return map;

		for (String param : m.group(3).substring(1).split("\\s*;\\s*")) {
			log.debug("parsing parameter: " + param);
			Matcher paramMat = parameterPattern.matcher(param);
			paramMat.matches();
			log.debug("parameter: " + paramMat.group(1) + "="
					+ paramMat.group(2));
			map.put(paramMat.group(1).trim(), paramMat.group(2));
		}
		return map;
	}

}
