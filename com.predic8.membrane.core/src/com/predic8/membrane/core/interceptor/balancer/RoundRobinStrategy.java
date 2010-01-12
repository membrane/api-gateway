package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.exchange.Exchange;


public class RoundRobinStrategy implements DispatchingStrategy {

	private int last;
	
	public void done(Exchange exc) {
		
	}

	public String dispatch(LoadBalancingInterceptor interceptor) {
		last ++;
		if (last >= interceptor.getEndpoints().size())
			last = 0;
		
		return interceptor.getEndpoints().get(last);
	}

}
