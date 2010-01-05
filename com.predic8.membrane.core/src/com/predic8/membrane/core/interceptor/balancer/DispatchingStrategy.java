package com.predic8.membrane.core.interceptor.balancer;


public interface DispatchingStrategy {

	public String dispatch(LoadBalancingInterceptor interceptor);
	
}
