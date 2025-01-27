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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.ws.relocator.*;
import org.slf4j.*;

import java.net.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.URLUtil.*;

/**
 * @description Rewrites the scheme, hostname and port in the "Location" header in HTTP responses,
 * as well as in the "Destination" header in HTTP requests. The rewriting reflects the different schemes,
 * hostnames and ports used to access Membrane  vs. the target HTTP server.
 * @topic 6. Misc
 */
@MCElement(name="reverseProxying")
public class ReverseProxyingInterceptor extends AbstractInterceptor {
	private static final Logger log = LoggerFactory.getLogger(ReverseProxyingInterceptor.class);

	public ReverseProxyingInterceptor() {
		name = "reverse proxy";
	}

	/**
	 * handles "Destination" header (see RFC 2518 section 9.3; also used by WebDAV)
	 */
	@Override
	public Outcome handleRequest(Exchange exc) {
		if (exc.getRequest() == null)
			return CONTINUE;
		String destination = exc.getRequest().getHeader().getFirstValue(DESTINATION);
		if (destination == null)
			return CONTINUE;
		if (!destination.contains("://"))
			return CONTINUE; // local redirect (illegal by spec)
		// do not rewrite, if the client does not refer to the same host
		if (!isSameSchemeHostAndPort(getProtocol(exc) + "://" + exc.getRequest().getHeader().getHost(), destination))
			return CONTINUE;
		// if we cannot determine the target hostname
		if (exc.getDestinations().isEmpty()) {
			// just remove the schema/hostname/port. this is illegal (by the spec),
			// but most clients understand it
            try {
                exc.getRequest().getHeader().setValue(DESTINATION, new URL(destination).getFile());
            } catch (MalformedURLException e) {
                log.error("Invalid destination URL: {}",destination, e);
            }
            return CONTINUE;
		}
        URL target;
        try {
            target = new URL(exc.getDestinations().getFirst());
        } catch (MalformedURLException e) {
			log.error("Could not parse target URL: {}",destination);
			internal(router.isProduction(),getDisplayName())
					.detail("Could not parse target URL")
					.internal("URL", exc.getDestinations().getFirst())
					.exception(e)
					.buildAndSetResponse(exc);
			return ABORT;
        }
        // rewrite to our schema, host and port
		exc.getRequest().getHeader().setValue(DESTINATION,
				Relocator.getNewLocation(destination, target.getProtocol(),
						target.getHost(), getPortFromURL(target), exc.getHandler().getContextPath(exc)));
		return CONTINUE;
	}

	/**
	 * Handles "Location" header.
	 */
	@Override
	public Outcome handleResponse(Exchange exc) {
		if (exc.getResponse() == null)
			return CONTINUE;
		String location = exc.getResponse().getHeader().getFirstValue(LOCATION);
		if (location == null)
			return CONTINUE;
		if (!location.contains("://"))
			return CONTINUE; // local redirect (illegal by spec)
		// do not rewrite, if the server did a redirect to some other hostname/port
		// (in which case we have to hope the hostname/port is valid on the client)
		if (!isSameSchemeHostAndPort(location, exc.getDestinations().getFirst()))
			return CONTINUE;
		// if we cannot determine the hostname we have been reached with (e.g. HTTP/1.0)
		if (exc.getOriginalHostHeaderHost() == null) {
			// just remove the schema/hostname/port. this is illegal (by the spec),
			// but most clients understand it
            try {
                exc.getResponse().getHeader().setValue(LOCATION, new URL(location).getFile());
            } catch (MalformedURLException e) {
				log.error("Could not parse URL for Location header: {}",location, e);
            }
            return CONTINUE;
		}
		// rewrite to our schema, host and port
		exc.getResponse().getHeader().setValue(LOCATION,
				Relocator.getNewLocation(location, getProtocol(exc),
						exc.getOriginalHostHeaderHost(), getPort(exc), exc.getHandler().getContextPath(exc)));
		return CONTINUE;
	}

	private boolean isSameSchemeHostAndPort(String location1, String location2) {
		try {
			if (location1.startsWith("/") || location2.startsWith("/"))
				return false; // no host info available
			URL loc2 = new URL(location2);
			URL loc1 = new URL(location1);
			if (loc2.getProtocol() != null && !loc2.getProtocol().equals(loc1.getProtocol()))
				return false;
			if (loc2.getHost() != null && !loc2.getHost().equals(loc1.getHost()))
				return false;
			return getPortFromURL(loc2) == getPortFromURL(loc1);
		} catch (MalformedURLException e) {
			if (e.getMessage().startsWith("unknown protocol:"))
				return false;
			log.warn("Location 1: {} Location 2: {}", location1, location2, e);
			return false;
		}
	}

	int getPort(Exchange exc) {
		return exc.getHandler().getLocalPort();
	}

	private String getProtocol(Exchange exc) {
		return exc.getProxy().getProtocol();
	}
}
