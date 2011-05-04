package com.predic8.membrane.core.interceptor.rewrite;

import java.util.Map;
import java.util.regex.Pattern;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class RegExURLRewriteInterceptor extends AbstractInterceptor {

	private Map<String, String> mapping;

	public RegExURLRewriteInterceptor() {
		priority = 150;
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = exc.getRequest().getUri();
		
		String regex = findFirstMatchingRegEx(uri);
		if (regex == null ) return Outcome.CONTINUE;
				
		exc.getRequest().setUri(replace(uri, regex));
		return Outcome.CONTINUE;
	}
	
	private String replace(String uri, String regex) {
		return uri.replaceAll(regex, mapping.get(regex));
	}

	private String findFirstMatchingRegEx(String uri) {
		for (String regex : mapping.keySet()) {
			if ( Pattern.matches(regex, uri) ) return regex;
		}
		return null;
	}

	public void setMapping(Map<String, String> mapping) {
		this.mapping = mapping;
	}
	

}
