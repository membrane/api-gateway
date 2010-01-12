package com.predic8.membrane.core.interceptor.balancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.predic8.membrane.core.exchange.Exchange;

public class ByThreadStrategy implements DispatchingStrategy {

	private int maxNumberOfThreadsPerEndpoint = 1;

	private Map<String, Integer> endpointCountMap = new HashMap<String, Integer>();
	
	public void done(Exchange exc) {
		String endPoint = exc.getRequestUri();
		if (endpointCountMap.containsKey(endPoint)) {
			Integer value = endpointCountMap.get(endPoint);
			value --;
			if (value == 0) {
				endpointCountMap.remove(endPoint); 
			} else {
				endpointCountMap.put(endPoint, value);
			}
		}
	}

	public String dispatch(LoadBalancingInterceptor interceptor) {
		List<String> endpoints = interceptor.getEndpoints();
		try {
			for(int j = 0; j < 5; j++) {
				for (String endpoint : endpoints) {
					if (endpointCountMap.containsKey(endpoint)) {
						Integer value = endpointCountMap.get(endpoint);
						if (value < maxNumberOfThreadsPerEndpoint) {
							value ++;
							endpointCountMap.put(endpoint, value);
							return endpoint;
						} else {
							continue;
						}
					} else {
						endpointCountMap.put(endpoint, 1);
						return endpoint;
					}
				}
				Thread.sleep(1000);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		throw new RuntimeException("All available servers are busy.");
	}
	
}
