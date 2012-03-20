package com.predic8.membrane.core.util;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import com.predic8.membrane.core.exchange.Exchange;

public class URLParamUtil {
	private static Pattern paramsPat = Pattern.compile("([^=]*)=?(.*)");
	
	public static Map<String, String> getParams(Exchange exc) throws Exception {		
		URI jUri = new URI(exc.getRequest().getUri());
		String q = jUri.getQuery();
		if (q == null) {
			if (hasNoFormParams(exc))
				return new HashMap<String, String>();
			q = new String(exc.getRequest().getBody().getRaw(), exc.getRequest().getCharset());
		}

		return parseQueryString(q);
	}

	private static boolean hasNoFormParams(Exchange exc) throws IOException {
		return !"application/x-www-form-urlencoded".equals(exc.getRequest()
				.getHeader().getContentType())
				|| exc.getRequest().isBodyEmpty();
	}

	
	public static String createQueryString( String... params ) throws UnsupportedEncodingException {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < params.length; i+=2) {
			if (i != 0) buf.append('&');
			buf.append(URLEncoder.encode(params[i], "UTF-8"));
			buf.append('=');
			buf.append(URLEncoder.encode(params[i+1], "UTF-8"));
		}		
		return buf.toString();
	}
	
	public static Map<String, String> parseQueryString(String query) throws UnsupportedEncodingException {
		Map<String, String> params = new HashMap<String, String>();
	
		for (String p : query.split("&")) {
			Matcher m = paramsPat.matcher(p);
			m.matches();
			params.put(URLDecoder.decode(m.group(1),"UTF-8"), 
					   URLDecoder.decode(m.group(2),"UTF-8"));
		}
		return params;
	}

}
