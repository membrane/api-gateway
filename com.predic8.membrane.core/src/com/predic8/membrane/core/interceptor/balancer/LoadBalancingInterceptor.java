package com.predic8.membrane.core.interceptor.balancer;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class LoadBalancingInterceptor extends AbstractInterceptor {

	private List<String> endpoints;

	private DispatchingStrategy dispatchingStrategy;

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String destination = getDestinationURL(dispatchingStrategy.dispatch(this), exc);
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
		dispatchingStrategy.done(exc);
		return Outcome.CONTINUE;
	}
	
	public String getDestinationURL(String hostAndPort, Exchange exc) throws MalformedURLException{
		String requestUri = exc.getOriginalRequestUri();
		if(requestUri.toLowerCase().startsWith("http://")) requestUri = new URL(requestUri).getFile();
		return "http://" + hostAndPort + requestUri;
	}

	public DispatchingStrategy getDispatchingStrategy() {
		return dispatchingStrategy;
	}

	public void setDispatchingStrategy(DispatchingStrategy strategy) {
		this.dispatchingStrategy = strategy;
	}

	public List<String> getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(List<String> endpoints) {
		this.endpoints = endpoints;
	}
}
