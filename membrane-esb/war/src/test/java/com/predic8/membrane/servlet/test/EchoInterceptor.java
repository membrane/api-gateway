package com.predic8.membrane.servlet.test;

import com.predic8.membrane.core.config.ElementName;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@ElementName("echo")
public class EchoInterceptor extends AbstractInterceptor {
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		return exc.echo();
	}

}
