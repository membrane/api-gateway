/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.balancer;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import com.predic8.membrane.core.exchange.AbstractExchange;

public class ByThreadStrategy implements DispatchingStrategy {

	private int maxNumberOfThreadsPerEndpoint = 5;

	private Map<String, Integer> endpointCount = new Hashtable<String, Integer>();

	private int retryTimeOnBusy = 1000;

	public void done(AbstractExchange exc) {
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
