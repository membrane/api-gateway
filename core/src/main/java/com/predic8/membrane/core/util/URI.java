/* Copyright 2014 predic8 GmbH, www.predic8.com

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

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Same behavior as {@link java.net.URI}, but accomodates '{' in paths.
 */
public class URI {
	private java.net.URI uri;

	private String input;
	private String path, query;
	private String pathDecoded, queryDecoded;

	private static Pattern PATTERN = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");
	//                                                 12            3  4          5       6   7        8 9
	// if defined, the groups are:
	// 2: scheme, 4: host, 5: path, 7: query, 9: fragment

	private boolean customInit(String s) {
		Matcher m = PATTERN.matcher(s);
		if (!m.matches())
			return false;
		input = s;
		path = m.group(5);
		query = m.group(7);
		return true;
	}

	URI(boolean allowCustomParsing, String s) throws URISyntaxException {
		try {
			uri = new java.net.URI(s);
		} catch (URISyntaxException e) {
			if (allowCustomParsing && customInit(s))
				return;
			throw e;
		}
	}

	URI(String s, boolean useCustomParsing) throws URISyntaxException {
		if (useCustomParsing) {
			if (!customInit(s))
				throw new URISyntaxException(s, "URI did not match regular expression.");
		} else {
			uri = new java.net.URI(s);
		}
	}

	public String getPath() {
		if (uri != null)
			return uri.getPath();
		if (pathDecoded == null)
			pathDecoded = decode(path);
		return pathDecoded;
	}

	public String getQuery() {
		if (uri != null)
			return uri.getQuery();
		if (queryDecoded == null)
			queryDecoded = decode(query);
		return queryDecoded;
	}

	private String decode(String string) {
		if (string == null)
			return string;
		try {
			return URLDecoder.decode(string, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public String getRawQuery() {
		if (uri != null)
			return uri.getRawQuery();
		return query;
	}

	@Override
	public String toString() {
		if (uri != null)
			return uri.toString();
		return input;
	}
}
