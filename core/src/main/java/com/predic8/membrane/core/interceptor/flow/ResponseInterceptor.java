package com.predic8.membrane.core.interceptor.flow;

import java.util.EnumSet;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCElement(name="response")
public class ResponseInterceptor extends AbstractFlowInterceptor {

	/**
	 * (Yes, this needs to be handled in handleREQUEST.)
	 */
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		for (Interceptor i : getInterceptors()) {
			EnumSet<Flow> f = i.getFlow();
			
			if (f.contains(Flow.RESPONSE))
				exc.pushInterceptorToStack(i);
		}
		return Outcome.CONTINUE;
	}

}
