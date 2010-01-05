package com.predic8.membrane.core.interceptor.balancer;


public class RoundRobinStrategy implements DispatchingStrategy {

	private int last;
	
	public String dispatch(LoadBalancingInterceptor interceptor) {
		last ++;
		if (last >= interceptor.getEndpoints().size())
			last = 0;
		
		return interceptor.getEndpoints().get(last);
	}

}
