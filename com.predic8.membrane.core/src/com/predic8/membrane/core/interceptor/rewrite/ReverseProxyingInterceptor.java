package com.predic8.membrane.core.interceptor.rewrite;

import java.net.MalformedURLException;
import java.net.URL;

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
		if (!location.contains("://"))
			return Outcome.CONTINUE; // local redirect (illegal by spec)
		// do not rewrite, if the server did a redirect to some other hostname/port
		// (in which case we have to hope the hostname/port is valid from the client's POV)
		if (!isSameHost(exc, location))
			return Outcome.CONTINUE;
		// if we cannot determine the hostname we have been reached with (e.g. HTTP/1.0)
		if (exc.getOriginalHostHeaderHost() == null) {
			// just remove the schema/hostname/port. this is illegal (by the spec),
			// but most clients understand it
			exc.getResponse().getHeader().setValue(Header.LOCATION, new URL(location).getFile());
			return Outcome.CONTINUE;
		}
		// rewrite to our schema, host and port
		exc.getResponse().getHeader().setValue(Header.LOCATION, 
				Relocator.getNewLocation(location, getProtocol(exc), 
						exc.getOriginalHostHeaderHost(), getPort(exc)));
		return Outcome.CONTINUE;
	}

	private boolean isSameHost(Exchange exc, String location)
			throws MalformedURLException {
		try {
			URL dest = new URL(exc.getRequest().getUri());
			URL loc = new URL(location);
			if (dest.getHost() != null && !dest.getHost().equals(loc.getHost()))
				return false;
			int destPort = dest.getPort() == -1 ? dest.getDefaultPort() : dest.getPort();
			int locPort = loc.getPort() == -1 ? loc.getDefaultPort() : loc.getPort();
			// TODO: debug this
			System.out.println(destPort + "  -  " + locPort);
			if (destPort != locPort)
				return false;
			return true;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	int getPort(Exchange exc) {
		return exc.getRule().getKey().getPort();
	}
	
	private String getProtocol(Exchange exc) {
		return "http";
	}


}
