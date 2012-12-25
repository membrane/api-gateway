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

import org.apache.commons.logging.*;

import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.ServiceProxy;

@MCInterceptor(xsd="" +
		"	<xsd:element name=\"dispatching\" type=\"EmptyElementType\" />\r\n" + 
		"")
public class DispatchingInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(DispatchingInterceptor.class.getName());
	
	public DispatchingInterceptor() {		
		name = "Dispatching Interceptor";		
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

	public static String getForwardingDestination(Exchange exc) throws Exception {
		ServiceProxy p = (ServiceProxy)exc.getRule();
		if (p.getTargetURL()!=null) {
			log.debug("destination: " + p.getTargetURL());
			return p.getTargetURL();
		}
		
		String url = new URL(p.getTargetScheme(), p.getTargetHost(), p.getTargetPort(), exc.getRequest().getUri()).toString();
		log.debug("destination: " + url);
		return url; 
	}
	
	@Override
	public String getHelpId() {
		return "dispatching";
	}

}
