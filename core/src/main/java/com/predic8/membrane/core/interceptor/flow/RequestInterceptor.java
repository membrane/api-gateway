/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.flow;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;

/**
 * @description Interceptors are usually applied to requests and responses. By nesting interceptors into a
 *              &lt;request&gt; Element you can limit their applictaion to requests only.
 */
public class RequestInterceptor extends AbstractFlowInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RequestInterceptor.class);

	public RequestInterceptor() {
		name = "Request Interceptor";
		setFlow(Flow.Set.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		boolean logDebug = log.isDebugEnabled();

		for (Interceptor i : getInterceptors()) {
			EnumSet<Flow> f = i.getFlow();
			if (!f.contains(Flow.REQUEST))
				continue;

			if (logDebug)
				log.debug("Invoking request handler: " + i.getDisplayName() + " on exchange: " + exc);

			Outcome o = i.handleRequest(exc);
			if (o != Outcome.CONTINUE)
				return o;
		}
		return Outcome.CONTINUE;
	}
}
