package com.predic8.membrane.core.interceptor.rewrite;

import java.util.Map;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class SimpleURLRewriteInterceptor extends AbstractInterceptor {

	private Map<String, String> mapping;

	public SimpleURLRewriteInterceptor() {
		priority = 150;
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = mapping.get(exc.getRequest().getUri());
		
		if (uri == null)
			return Outcome.CONTINUE;
		
		exc.getRequest().setUri(uri);
		return Outcome.CONTINUE;
	}
	
	public void setMapping(Map<String, String> mapping) {
		this.mapping = mapping;
	}
	

}
