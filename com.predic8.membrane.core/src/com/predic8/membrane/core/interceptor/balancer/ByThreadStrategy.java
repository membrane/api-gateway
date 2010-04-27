package com.predic8.membrane.core.interceptor.balancer;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import com.predic8.membrane.core.exchange.Exchange;

public class ByThreadStrategy implements DispatchingStrategy {

	private int maxNumberOfThreadsPerEndpoint = 5;

	private Map<String, Integer> endpointCount = new Hashtable<String, Integer>();

	private int retryTimeOnBusy = 1000;

	public void done(Exchange exc) {
		String endPoint = exc.getOriginalRequestUri();
		if (endpointCount.containsKey(endPoint)) {
			Integer counter = endpointCount.get(endPoint);
			counter--;
			if (counter == 0) {
				endpointCount.remove(endPoint);
			} else {
				endpointCount.put(endPoint, counter);
			}
		}
	}

	public String dispatch(LoadBalancingInterceptor interceptor) {
		List<String> endpoints = interceptor.getEndpoints();

		for (int j = 0; j < 5; j++) {
			for (String endpoint : endpoints) {
				if (!endpointCount.containsKey(endpoint)) {
					endpointCount.put(endpoint, 1);
					return endpoint;
				}

				Integer counter = endpointCount.get(endpoint);
				if (counter < maxNumberOfThreadsPerEndpoint) {
					counter++;
					endpointCount.put(endpoint, counter);
					return endpoint;
				} else {
					continue;
				}

			}
			try {
				Thread.sleep(retryTimeOnBusy);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		throw new RuntimeException("All available servers are busy.");
	}

	public void setMaxNumberOfThreadsPerEndpoint(int maxNumberOfThreadsPerEndpoint) {
		this.maxNumberOfThreadsPerEndpoint = maxNumberOfThreadsPerEndpoint;
	}

	public void setRetryTimeOnBusy(int retryTimeOnBusy) {
		this.retryTimeOnBusy = retryTimeOnBusy;
	}
	
	

}
