package com.predic8.membrane.servlet.test;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCElement(name="echo")
public class EchoInterceptor extends AbstractInterceptor {
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Outcome outcome = exc.echo();
		exc.getResponse().getHeader().removeFields(Header.CONTENT_LENGTH);
		String body = exc.getRequest().getUri() + "\n" + new String(exc.getRequest().getBody().getContent(), Constants.UTF_8_CHARSET);
		exc.getResponse().setBodyContent(body.getBytes(Constants.UTF_8_CHARSET));
		return outcome;
	}

}
