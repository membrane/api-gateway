/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.rules.ServiceProxy;

public class DispatchingInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(DispatchingInterceptor.class.getName());
	
	public DispatchingInterceptor() {		
		name = "Dispatching Interceptor";		
		priority = 200;
		setFlow(Flow.REQUEST);
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
	
		if (exc.getRule() instanceof ServiceProxy) {
			exc.getDestinations().add(getForwardingDestination(exc));	
			return Outcome.CONTINUE;
		}

		exc.getDestinations().add(exc.getRequest().getUri());	
	
		return Outcome.CONTINUE;
	}

	private String getForwardingDestination(Exchange exc) {
		StringBuffer buf = new StringBuffer();
		buf.append("http://");
		buf.append(((ServiceProxy)exc.getRule()).getTargetHost());
		buf.append(":");
		buf.append(((ServiceProxy)exc.getRule()).getTargetPort() );
		buf.append(exc.getRequest().getUri());
		log.debug("destination: " + buf.toString());
		return buf.toString();
	}

}
