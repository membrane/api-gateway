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
