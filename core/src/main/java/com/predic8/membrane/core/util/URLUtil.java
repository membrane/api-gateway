/* Copyright 2013 predic8 GmbH, www.predic8.com

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

import java.net.URI;

public class URLUtil {

	public static String getPathFromPathQuery(String pathQuery) {
		int i = pathQuery.indexOf('?');
		return i == -1 ? pathQuery : pathQuery.substring(0, i);
	}

	public static String getPathQuery(String uri) {
		try {
			URI u = new URI(uri);
			String query = u.getQuery();
			return u.getPath() + (query == null ? "" : "?" + query);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String getName(String uri) {
		try {
			URI u = new URI(uri);
			String p = u.getPath();
			int i = p.lastIndexOf('/');
			return i == -1 ? p : p.substring(i+1);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
