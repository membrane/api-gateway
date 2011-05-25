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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;

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

	// Header field values

	public static final String CHUNKED = "chunked";

	private Log log = LogFactory.getLog(Header.class.getName());

	private Vector<HeaderField> fields = new Vector<HeaderField>();

	public Header() {
	}

	public Header(InputStream in, StringBuffer rawMessage) throws IOException, EndOfStreamException {
		String line;

		while ((line = HttpUtil.readLine(in)).length() > 0) {
			try {
				addLineToRawMessage(rawMessage, line);

				add(new HeaderField(line));
			} catch (StringIndexOutOfBoundsException sie) {
				log.error("Header read line that caused problems: " + line);
			}
		}
	}

	private void addLineToRawMessage(StringBuffer rawMessage, String line) {
		if (rawMessage != null)
			rawMessage.append(line).append(Constants.CRLF);
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
			if (field.getHeaderName().equals(new HeaderName(name)))
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
		HeaderName nameToFind = new HeaderName(name); 
		for (HeaderField field : fields) {
			if (field.getHeaderName().equals(nameToFind))
				return field.getValue();
		}
		return null;
	}

	public Object[] getAllHeaderFields() {
		return fields.toArray();
	}

	public void write(OutputStream out) throws IOException {
		StringBuffer buffer = new StringBuffer();
		for (HeaderField field : fields) {
			String name = field.getHeaderName().toString();
			String value = field.getValue();
			buffer.append(name).append(": ").append(value).append(Constants.CRLF);
		}
		out.write(buffer.toString().getBytes());
	}

	// Only change the header by once.
	public HeaderField setValue(String name, String value) {
		HeaderName headerName = new HeaderName(name);
		for (HeaderField field : fields) {
			if (field.getHeaderName().equals(headerName)) {
				field.setValue(value);
				return field;
			}
		}
		HeaderField newField = new HeaderField(headerName, value);
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
	
	public void setContentType(String value) {
		add(CONTENT_TYPE, value);
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

	public void setXForwardedFor(String value) {
		add(X_FORWARDED_FOR, value);
	}

	public String getXForwardedFor() {
		return getFirstValue(X_FORWARDED_FOR);
	}

	public String getContentEncoding() {
		return getFirstValue(CONTENT_ENCODING);
	}
	
	//TODO header value is a complex unit
	public String getCharset() {
		String type = getContentType();
		
		if (type == null)
			return Constants.ISO_8859_1;
		
		int idx = type.indexOf("charset=");
		if (idx < 0)
			return Constants.ISO_8859_1;
		
		return type.substring(idx + 8);
	}
	

}