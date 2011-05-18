package com.predic8.membrane.core.interceptor.rewrite;

import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.rest.HTTP2XMLInterceptor;

public class RegExURLRewriteInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(RegExURLRewriteInterceptor.class.getName());
	
	private Map<String, String> mapping;

	public RegExURLRewriteInterceptor() {
		priority = 150;
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = exc.getRequest().getUri();
		
		log.debug("uri: "+uri);

		String regex = findFirstMatchingRegEx(uri);
		if (regex == null ) return Outcome.CONTINUE;
		
		log.debug("match found: "+regex);
		log.debug("replacing with: "+mapping.get(regex));		
		
		exc.getRequest().setUri(replace(uri, regex));
		return Outcome.CONTINUE;
	}
	
	private String replace(String uri, String regex) {		
		String replaced = uri.replaceAll(regex, mapping.get(regex));
		
		log.debug("replaced URI: "+replaced);
		
		return replaced;
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
