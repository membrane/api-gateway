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

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.AbstractServiceProxy;

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

	private static Logger log = LoggerFactory.getLogger(DispatchingInterceptor.class.getName());

	public DispatchingInterceptor() {
		name = "Dispatching Interceptor";
		setFlow(Flow.Set.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {

		if (exc.getRule() instanceof AbstractServiceProxy) {
			exc.getDestinations().add(getForwardingDestination(exc));
			return Outcome.CONTINUE;
		}

		exc.getDestinations().add(exc.getRequest().getUri());

		return Outcome.CONTINUE;
	}

	public static String getForwardingDestination(Exchange exc) throws Exception {
		AbstractServiceProxy p = (AbstractServiceProxy)exc.getRule();
		if (p.getTargetURL()!=null) {
			log.debug("destination: " + p.getTargetURL());
			return p.getTargetURL();
		}

		if (p.getTargetHost() != null) {
			String url = new URL(p.getTargetScheme(), p.getTargetHost(), p.getTargetPort(), exc.getRequest().getUri()).toString();
			log.debug("destination: " + url);
			return url;
		}

		return exc.getRequest().getUri();
	}

}
