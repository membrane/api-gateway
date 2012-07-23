/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
		name = "Reverse Proxy";
		setFlow(Flow.RESPONSE);
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		if (exc.getResponse() == null)
			return Outcome.CONTINUE;
		String location = exc.getResponse().getHeader().getFirstValue(Header.LOCATION);
		if (location == null)
			return Outcome.CONTINUE;
		if (!location.contains("://"))
			return Outcome.CONTINUE; // local redirect (illegal by spec)
		// do not rewrite, if the server did a redirect to some other hostname/port
		// (in which case we have to hope the hostname/port is valid on the client)
		if (!isSameHost(exc.getDestinations().get(0), location))
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

	private boolean isSameHost(String location2, String location)
			throws MalformedURLException {
		try {
			if (location.startsWith("/"))
				return false; // no host info available
			URL loc2 = new URL(location2);
			URL loc1 = new URL(location);
			if (loc2.getHost() != null && !loc2.getHost().equals(loc1.getHost()))
				return false;
			int loc2Port = loc2.getPort() == -1 ? loc2.getDefaultPort() : loc2.getPort();
			int loc1Port = loc1.getPort() == -1 ? loc1.getDefaultPort() : loc1.getPort();
			if (loc2Port != loc1Port)
				return false;
			return true;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	int getPort(Exchange exc) {
		return exc.getHandler().getLocalPort();
	}
	
	private String getProtocol(Exchange exc) {
		return "http";
	}

}
