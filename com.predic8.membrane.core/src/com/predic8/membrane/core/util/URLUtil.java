package com.predic8.membrane.core.util;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class URLUtil {
	private static Pattern paramsPat = Pattern.compile("([^=]*)=?(.*)");
	
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
