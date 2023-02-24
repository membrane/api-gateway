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

import java.net.*;

public class URLUtil {

	public static String getHost(String uri) {
		int i = uri.indexOf(":") + 1;
		while (uri.charAt(i) == '/')
			i++;
		int j = uri.indexOf("/", i);
		return j == -1 ? uri.substring(i) : uri.substring(i, j);
	}

	public static String getPathQuery(URIFactory uriFactory, String uri) throws URISyntaxException {
		URI u = uriFactory.create(uri);
		String query = u.getRawQuery();
		String path = u.getRawPath();
		return (path.isEmpty() ? "/" : path) + (query == null ? "" : "?" + query);
	}

	public static String getName(URIFactory uriFactory, String uri) throws URISyntaxException {
		URI u = uriFactory.create(uri);
		String p = u.getPath();
		int i = p.lastIndexOf('/');
		return i == -1 ? p : p.substring(i+1);
	}

	public static int getPortFromURL(URL loc2) {
		return loc2.getPort() == -1 ? loc2.getDefaultPort() : loc2.getPort();
	}
}
