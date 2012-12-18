package com.predic8.membrane.core.util;

import java.net.URI;

public class URLUtil {

	public static String getPath(String uri) {
		try {
			return new URI(uri).getPath();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String getPathQuery(String uri) {
		try {
			URI u = new URI(uri);
			return u.getPath() + u.getQuery();
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
