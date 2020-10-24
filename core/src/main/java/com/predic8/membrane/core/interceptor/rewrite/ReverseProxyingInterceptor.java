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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.ws.relocator.Relocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @description Rewrites the scheme, hostname and port in the "Location" header in HTTP responses,
 * as well as in the "Destination" header in HTTP requests. The rewriting reflects the different schemes,
 * hostnames and ports used to access Membrane Service Proxy vs. the target HTTP server.
 * @topic 4. Interceptors/Features
 */
@MCElement(name="reverseProxying")
public class ReverseProxyingInterceptor extends AbstractInterceptor {
	private static final Logger log = LoggerFactory.getLogger(ReverseProxyingInterceptor.class);

	public ReverseProxyingInterceptor() {
		name = "Reverse Proxy";
	}

	/**
	 * handles "Destination" header (see RFC 2518 section 9.3; also used by WebDAV)
	 */
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRequest() == null)
			return Outcome.CONTINUE;
		String destination = exc.getRequest().getHeader().getFirstValue(Header.DESTINATION);
		if (destination == null)
			return Outcome.CONTINUE;
		if (!destination.contains("://"))
			return Outcome.CONTINUE; // local redirect (illegal by spec)
		// do not rewrite, if the client does not refer to the same host
		if (!isSameSchemeHostAndPort(getProtocol(exc) + "://" + exc.getRequest().getHeader().getHost(), destination))
			return Outcome.CONTINUE;
		// if we cannot determine the target hostname
		if (exc.getDestinations().isEmpty()) {
			// just remove the schema/hostname/port. this is illegal (by the spec),
			// but most clients understand it
			exc.getRequest().getHeader().setValue(Header.DESTINATION, new URL(destination).getFile());
			return Outcome.CONTINUE;
		}
		URL target = new URL(exc.getDestinations().get(0));
		// rewrite to our schema, host and port
		exc.getRequest().getHeader().setValue(Header.DESTINATION,
				Relocator.getNewLocation(destination, target.getProtocol(),
						target.getHost(), target.getPort() == -1 ? target.getDefaultPort() : target.getPort(), exc.getHandler().getContextPath(exc)));
		return Outcome.CONTINUE;
	}

	/**
	 * Handles "Location" header.
	 */
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
		if (!isSameSchemeHostAndPort(location, exc.getDestinations().get(0)))
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
						exc.getOriginalHostHeaderHost(), getPort(exc), exc.getHandler().getContextPath(exc)));
		return Outcome.CONTINUE;
	}

	private boolean isSameSchemeHostAndPort(String location2, String location)
			throws MalformedURLException {
		try {
			if (location.startsWith("/") || location2.startsWith("/"))
				return false; // no host info available
			URL loc2 = new URL(location2);
			URL loc1 = new URL(location);
			if (loc2.getProtocol() != null && !loc2.getProtocol().equals(loc1.getProtocol()))
				return false;
			if (loc2.getHost() != null && !loc2.getHost().equals(loc1.getHost()))
				return false;
			int loc2Port = loc2.getPort() == -1 ? loc2.getDefaultPort() : loc2.getPort();
			int loc1Port = loc1.getPort() == -1 ? loc1.getDefaultPort() : loc1.getPort();
			if (loc2Port != loc1Port)
				return false;
			return true;
		} catch (MalformedURLException e) {
			if (e.getMessage().startsWith("unknown protocol:"))
				return false;
			log.warn("Location: " + location + " Location2: " + location2, e); // TODO: fix these cases
			return false;
		}
	}

	int getPort(Exchange exc) {
		return exc.getHandler().getLocalPort();
	}

	private String getProtocol(Exchange exc) {
		Rule r = exc.getRule();
		return r != null && r instanceof AbstractServiceProxy && ((AbstractServiceProxy)r).getSslInboundContext() != null ? "https" : "http";
	}

}
