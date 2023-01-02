/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.rules.*;
import org.slf4j.*;

import java.net.*;

import static com.predic8.membrane.core.util.URLUtil.*;

/**
 * @description This interceptor adds the destination specified in the target
 *              element to the list of destinations of the exchange object. It
 *              must be placed into the transport to make Service Proxies Work
 *              properly. It has to be placed after the ruleMatching
 *              interceptor. The ruleMatching interceptor looks up a service
 *              proxy for an incoming request and places it into the exchange
 *              object. The dispatching interceptor needs the service proxy to
 *              get information about the target.
 */
@MCElement(name="dispatching")
public class DispatchingInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(DispatchingInterceptor.class.getName());

	public DispatchingInterceptor() {
		name = "Dispatching Interceptor";
		setFlow(Flow.Set.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {

		if (exc.getRule() instanceof AbstractServiceProxy) {
			exc.getDestinations().add(getForwardingDestination(exc));
			setSNIPropertyOnExchange(exc);
			return Outcome.CONTINUE;
		}

		exc.getDestinations().add(exc.getRequest().getUri());

		return Outcome.CONTINUE;
	}

	private void setSNIPropertyOnExchange(Exchange exc) {
		AbstractServiceProxy asp = (AbstractServiceProxy) exc.getRule();
		if(asp.getTargetSSL() != null) {
			String sni = asp.getTargetSSL().getServerName();
			if (sni != null)
				exc.setProperty(Exchange.SNI_SERVER_NAME, sni);
		}
	}

	public static String getForwardingDestination(Exchange exc) throws Exception {
		String urlResult = null;

		if(exc.getRule() instanceof InternalProxy)
			urlResult = handleInternalProxy(exc);
		if(exc.getRule() instanceof AbstractServiceProxy)
			urlResult = handleAbstractServiceProxy(exc);

		log.debug("destination: " + urlResult);
		return urlResult != null ? urlResult : exc.getRequest().getUri();
	}

	private static String handleInternalProxy(Exchange exc) throws MalformedURLException {
		InternalProxy ip = (InternalProxy) exc.getRule();

		if(ip.getTarget() == null)
			return null;

		if(ip.getTarget().getUrl() != null)
			return ip.getTarget().getUrl();
		if(ip.getTarget().getHost() != null)
			return new URL(ip.getTarget().getSslParser() != null ? "https" : "http", ip.getTarget().getHost(), ip.getTarget().getPort(), exc.getRequest().getUri()).toString();

		return null;
	}

	protected static String handleAbstractServiceProxy(Exchange exc) throws MalformedURLException, URISyntaxException {
		AbstractServiceProxy p = (AbstractServiceProxy) exc.getRule();

		if (p.getTargetURL() != null) {
			if (p.getTargetURL().startsWith("service:") && !p.getTargetURL().contains("/")) {
				return "service://" + getHost(p.getTargetURL()) + exc.getRequest().getUri();
			}
			// @TODO UriUtil ersetzen
			if (p.getTargetURL().startsWith("http") && !UriUtil.getPathFromURL(p.getTargetURL()).contains("/")) {
				return p.getTargetURL() + exc.getRequestURI();
			}
			return p.getTargetURL();
		}
		if (p.getTargetHost() != null)
			return new URL(p.getTargetScheme(), p.getTargetHost(), p.getTargetPort(), exc.getRequest().getUri()).toString();

		return null;
	}
}
