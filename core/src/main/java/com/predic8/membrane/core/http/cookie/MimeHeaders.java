/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.http.cookie;

import com.predic8.membrane.core.http.*;

import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.*;

/**
 * Adapter between Tomcat classes ({@link ServerCookie} etc.) and Membrane
 * classes ({@link Request} etc.).
 */
public class MimeHeaders {

	private final HeaderField[] header;

	public MimeHeaders(Header header) {
		this.header = header.getAllHeaderFields();
	}

	public int findHeader(String string, int pos) {
		while (true) {
			if (pos >= header.length)
				return -1;
			if (header[pos].getHeaderName().hasName(string))
				return pos;
			pos++;
		}
	}

	public List<MessageBytes> findHeaders(String string) {
		List<MessageBytes> result = new ArrayList<>();
		int pos = 0;
		while (true) {
			pos = findHeader(string, pos);
			if (pos < 0)
				return result;
			MessageBytes cookieValue = getValue(pos++);
			if (cookieValue != null && !cookieValue.isNull())
				result.add(cookieValue);
		}

	}

	public MessageBytes getValue(int pos) {
		MessageBytes b = MessageBytes.newInstance();
		byte[] buf = header[pos].getValue().getBytes(ISO_8859_1);
		b.setBytes(buf, 0, buf.length);
		return b;
	}

}
