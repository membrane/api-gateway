package com.predic8.membrane.core.interceptor.custom;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.*;

public class MyInterceptor extends AbstractInterceptor {

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		System.out.println("MyInterceptor invoked");
		return Outcome.CONTINUE;
	}

}
