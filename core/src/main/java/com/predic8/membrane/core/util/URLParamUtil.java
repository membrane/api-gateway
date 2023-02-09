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

package com.predic8.membrane.core.util;

import com.predic8.membrane.core.exchange.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static java.net.URLDecoder.*;
import static java.nio.charset.StandardCharsets.*;

public class URLParamUtil {
	private static final Pattern paramsPat = Pattern.compile("([^=]*)=?(.*)");

	public static Map<String, String> getParams(URIFactory uriFactory, Exchange exc, DuplicateKeyOrInvalidFormStrategy duplicateKeyOrInvalidFormStrategy) throws Exception {
		URI jUri = uriFactory.create(exc.getRequest().getUri());
		String q = jUri.getRawQuery();
		if (q == null) {
			if (hasNoFormParams(exc))
				return new HashMap<>();
			q = new String(exc.getRequest().getBody().getContent(), exc.getRequest().getCharset());
		}

		return parseQueryString(q, duplicateKeyOrInvalidFormStrategy);
	}

	public static String getStringParam(URIFactory uriFactory, Exchange exc, String name) throws Exception {
		return getParams(uriFactory, exc, ERROR).get(name);
	}

	public static int getIntParam(URIFactory uriFactory, Exchange exc, String name) throws Exception {
		return Integer.parseInt(getParams(uriFactory, exc, ERROR).get(name));
	}


	public static boolean hasNoFormParams(Exchange exc) throws IOException {
		// @TODO turn around in hasFormParams!
		return !isWWWFormUrlEncoded(exc.getRequest().getHeader().getContentType()) || exc.getRequest().isBodyEmpty();
	}


	public static String createQueryString( String... params ) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < params.length; i+=2) {
			if (i != 0) buf.append('&');
			buf.append(URLEncoder.encode(params[i], StandardCharsets.UTF_8));
			buf.append('=');
			buf.append(URLEncoder.encode(params[i+1], StandardCharsets.UTF_8));
		}
		return buf.toString();
	}

	public enum DuplicateKeyOrInvalidFormStrategy {
		ERROR,
		MERGE_USING_COMMA
	}

	/**
	 * Parse a URL query into parameter pairs. The query is expected to be application/x-www-form-urlencoded .
	 *
	 * Note that this method does not really support multiple parameters with the same key. <b>This method should
	 * therefore only be used in contexts where this is not an issue.</b>
	 *
	 * Background:
	 * Note that according to the original RFC 3986 Section 3.4, there is no defined format of the query string.
	 *
	 * HTML5 defines in <a href="https://html.spec.whatwg.org/#form-submission">form-submission</a> how HTML forms should be serialized.
	 * The URLSearchParams class behaviour is defined in <a href="https://url.spec.whatwg.org/#concept-urlsearchparams-list">URLSearchParams</a>
	 * where handling of parameters with the same key is supported.
	 */
	public static Map<String, String> parseQueryString(String query, DuplicateKeyOrInvalidFormStrategy duplicateKeyOrInvalidFormStrategy) {
		Map<String, String> params = new HashMap<>();

		for (String p : query.split("&")) {
			Matcher m = paramsPat.matcher(p);
			if (m.matches()) {
				String key = decode(m.group(1), UTF_8);
				String value = decode(m.group(2), UTF_8);
				String oldValue = params.get(key);
				if (oldValue == null)
					params.put(key, value);
				else
					switch (duplicateKeyOrInvalidFormStrategy) {
						case ERROR -> throw new RuntimeException("Could not parse query: " + query);
						case MERGE_USING_COMMA -> params.put(key, oldValue + "," + value);
					}
			} else {
				if (duplicateKeyOrInvalidFormStrategy == ERROR)
					throw new RuntimeException("Could not parse query: " + query);
			}
		}
		return params;
	}

	public static String encode(Map<String, String> params) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;

		for (Map.Entry<String, String> p : params.entrySet()) {
			if (first)
				first = false;
			else
				sb.append("&");
			sb.append(URLEncoder.encode(p.getKey(), UTF_8));
			sb.append("=");
			sb.append(URLEncoder.encode(p.getValue(), UTF_8));
		}

		return sb.toString();
	}

	public static class ParamBuilder {
		HashMap<String, String> params = new HashMap<>();

		public ParamBuilder add(String key, String value) {
			params.put(key, value);
			return this;
		}

		public String build() {
			return encode(params);
		}
	}
}
