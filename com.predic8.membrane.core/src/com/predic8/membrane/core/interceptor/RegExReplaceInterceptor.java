package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;

public class RegExReplaceInterceptor extends AbstractInterceptor {

	private String pattern;
	
	private String replacement;
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
	
		Response res = exc.getResponse();
		
		if (res.hasNoContent())
			return Outcome.CONTINUE;
		
		res.readBody();
		byte[] content = res.getBody().getContent();
		res.setBodyContent(new String(content).replaceAll(pattern, replacement).getBytes());
		
		return Outcome.CONTINUE;
	}

	public String getPattern() {
		return pattern;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	public String getReplacement() {
		return replacement;
	}

	public void setReplacement(String replacement) {
		this.replacement = replacement;
	}
	
}
