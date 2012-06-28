package com.predic8.membrane.core.interceptor.rest;

import java.util.Map;
import java.util.regex.Matcher;

public class QueryParameter {
	private Map<String, String> params;
	private Matcher matcher;
	
	public QueryParameter(Map<String, String> params, Matcher matcher) {
		this.params = params;
		this.matcher = matcher;
	}
	
	public int getInt(String name, int def) {
		if (params.containsKey(name)) {
			return getInt(name);
		}
		return def;
	}
	
	public int getInt(String name) {
		return Integer.parseInt(params.get(name));
	}

	public String getString(String name) {
		return params.get(name);
	}

	public int getGroupInt(int i) {
		return Integer.parseInt(getGroup(i));
	}
	
	public String getGroup(int i) {
		return matcher.group(i);
	}
	
}
