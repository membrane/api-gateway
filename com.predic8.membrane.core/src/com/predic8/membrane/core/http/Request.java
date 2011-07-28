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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;

public class Request extends Message {

	protected static Log log = LogFactory.getLog(Request.class.getName());

	public static final String METHOD_GET = "GET";

	public static final String METHOD_POST = "POST";

	public static final String METHOD_HEAD = "HEAD";

	public static final String METHOD_DELETE = "DELETE";

	public static final String METHOD_PUT = "PUT";

	public static final String METHOD_TRACE = "TRACE";

	public static final String METHOD_CONNECT = "CONNECT";

	private static Pattern pattern = Pattern.compile("(.+?) (.+?) HTTP/(.+?)$");

	String method;
	String uri;

	public void parseStartLine(InputStream in) throws IOException, EndOfStreamException {
		Matcher matcher = pattern.matcher(HttpUtil.readLine(in));
		matcher.find();
		method = matcher.group(1);
		uri = matcher.group(2);
		version = matcher.group(3);
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	@Override
	public String getStartLine() {
		StringBuffer buf = new StringBuffer();
		buf.append(method);
		buf.append(" ");
		buf.append(uri);
		buf.append(" HTTP/");
		buf.append(version);
		buf.append(Constants.CRLF);
		return buf.toString();
	}

	protected void createBody(InputStream in) throws IOException {
		log.debug("createBody");

		if (isBodyEmpty()) {
			log.debug("empty body created");
			body = new EmptyBody();
			return;
		}

		super.createBody(in);
	}

	public boolean isHEADRequest() {
		return METHOD_HEAD.equals(method);
	}

	public boolean isGETRequest() {
		return METHOD_GET.equals(method);
	}

	public boolean isPOSTRequest() {
		return METHOD_POST.equals(method);
	}

	public boolean isDELETERequest() {
		return METHOD_DELETE.equals(method);
	}

	public boolean isCONNECTRequest() {
		return METHOD_CONNECT.equals(method);
	}

	@Override
	public String getName() {
		return uri;
	}

	@Override
	public boolean isBodyEmpty() throws IOException {

		if (isGETRequest() || isHEADRequest() || isCONNECTRequest())
			return true;

		if (isDELETERequest()) {
			if (header.hasContentLength())
				return header.getContentLength() == 0;

			if (getBody() instanceof ChunkedBody) {
				return false;
			}
			return true;
		}

		return super.isBodyEmpty();
	}

}
