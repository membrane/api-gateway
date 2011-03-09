package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.exchange.Exchange;


public interface DispatchingStrategy {

	public String dispatch(LoadBalancingInterceptor interceptor);

	public void done(Exchange exc);
	
}
