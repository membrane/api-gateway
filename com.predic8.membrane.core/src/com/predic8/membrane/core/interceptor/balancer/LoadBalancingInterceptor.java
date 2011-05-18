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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class LoadBalancingInterceptor extends AbstractInterceptor {

	private List<String> endpoints;

	private DispatchingStrategy strategy;

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String destination = getDestinationURL(strategy.dispatch(this), exc);
		exc.setOriginalRequestUri(destination);
		exc.getDestinations().clear();
		exc.getDestinations().add(destination);

		for (String dest : endpoints) {
			if (!dest.equals(destination))
				exc.getDestinations().add(getDestinationURL(dest, exc));
		}

		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		strategy.done(exc);
		return Outcome.CONTINUE;
	}
	
	public String getDestinationURL(String hostAndPort, Exchange exc) throws MalformedURLException{
		return "http://" + hostAndPort + getRequestURI(exc);
	}

	private String getRequestURI(Exchange exc) throws MalformedURLException {
		if(exc.getOriginalRequestUri().toLowerCase().startsWith("http://")) 
			return new URL(exc.getOriginalRequestUri()).getFile();
		
		return exc.getOriginalRequestUri();
	}

	public DispatchingStrategy getDispatchingStrategy() {
		return strategy;
	}

	public void setDispatchingStrategy(DispatchingStrategy strategy) {
		this.strategy = strategy;
	}

	public List<String> getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(List<String> endpoints) {
		this.endpoints = endpoints;
	}
}
