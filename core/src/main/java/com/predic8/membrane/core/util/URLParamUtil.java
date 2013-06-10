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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;

public class URLParamUtil {
	private static Pattern paramsPat = Pattern.compile("([^=]*)=?(.*)");
	
	public static Map<String, String> getParams(Exchange exc) throws Exception {		
		URI jUri = new URI(exc.getRequest().getUri());
		String q = jUri.getQuery();
		if (q == null) {
			if (hasNoFormParams(exc))
				return new HashMap<String, String>();
			q = new String(exc.getRequest().getBody().getContent(), exc.getRequest().getCharset());
		}

		return parseQueryString(q);
	}

	public static String getStringParam(Exchange exc, String name) throws Exception {
		return getParams(exc).get(name);
	}

	public static int getIntParam(Exchange exc, String name) throws Exception {
		return Integer.parseInt(getParams(exc).get(name));
	}

	
	private static boolean hasNoFormParams(Exchange exc) throws IOException {
		return !"application/x-www-form-urlencoded".equals(exc.getRequest()
				.getHeader().getContentType())
				|| exc.getRequest().isBodyEmpty();
	}

	
	public static String createQueryString( String... params ) {
		try {
			StringBuilder buf = new StringBuilder();
			for (int i = 0; i < params.length; i+=2) {
				if (i != 0) buf.append('&');
				buf.append(URLEncoder.encode(params[i], Constants.UTF_8));
				buf.append('=');
				buf.append(URLEncoder.encode(params[i+1], Constants.UTF_8));
			}		
			return buf.toString();
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Map<String, String> parseQueryString(String query) {
		try {
			Map<String, String> params = new HashMap<String, String>();

			for (String p : query.split("&")) {
				Matcher m = paramsPat.matcher(p);
				m.matches();
				params.put(URLDecoder.decode(m.group(1), Constants.UTF_8), 
						URLDecoder.decode(m.group(2), Constants.UTF_8));
			}
			return params;
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static String encode(Map<String, String> params) {
		try {
			StringBuilder sb = new StringBuilder();
			boolean first = true;

			for (Map.Entry<String, String> p : params.entrySet()) {
				if (first)
					first = false;
				else
					sb.append("&");
				sb.append(URLEncoder.encode(p.getKey(), Constants.UTF_8));
				sb.append("=");
				sb.append(URLEncoder.encode(p.getValue(), Constants.UTF_8));
			}

			return sb.toString();
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static class ParamBuilder {
		HashMap<String, String> params = new HashMap<String, String>();

		public ParamBuilder add(String key, String value) {
			params.put(key, value);
			return this;
		}
		
		public String build() {
			return encode(params);
		}
	}
}
