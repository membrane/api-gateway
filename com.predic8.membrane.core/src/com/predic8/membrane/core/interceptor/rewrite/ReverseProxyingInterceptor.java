package com.predic8.membrane.core.interceptor.rewrite;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.ws.relocator.Relocator;

/**
 * Rewrites the "Location" header in responses.
 */
public class ReverseProxyingInterceptor extends AbstractInterceptor {
	public ReverseProxyingInterceptor() {
		setFlow(Flow.RESPONSE);
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		String location = exc.getResponse().getHeader().getFirstValue(Header.LOCATION);
		if (location == null)
			return Outcome.CONTINUE;
		if (!location.startsWith("http"))
			return Outcome.CONTINUE;
		exc.getResponse().getHeader().setValue(Header.LOCATION, 
				Relocator.getNewLocation(location, getProtocol(exc), getHost(exc), getPort(exc)));
		return Outcome.CONTINUE;
	}
	
	int getPort(Exchange exc) {
		return exc.getRule().getKey().getPort();
	}
	
	private String getHost(Exchange exc) {
	    String locHost =  exc.getOriginalHostHeaderHost();
	    if (locHost == null) {
			return "localhost";
		}
		return locHost;
	}
	
	private String getProtocol(Exchange exc) {
		return "http";
	}


}
