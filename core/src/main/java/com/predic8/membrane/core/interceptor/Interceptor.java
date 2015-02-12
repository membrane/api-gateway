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

import java.util.EnumSet;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;

public interface Interceptor {

	public enum Flow {
		REQUEST, RESPONSE, ABORT;
		
		public static class Set {
			public static final EnumSet<Flow> REQUEST = EnumSet.of(Flow.REQUEST);
			public static final EnumSet<Flow> RESPONSE = EnumSet.of(Flow.RESPONSE, Flow.ABORT);
			public static final EnumSet<Flow> REQUEST_RESPONSE = EnumSet.of(Flow.REQUEST, Flow.RESPONSE, Flow.ABORT);
		}
	}
	
	public Outcome handleRequest(Exchange exc) throws Exception;
	public Outcome handleResponse(Exchange exc) throws Exception;
	
	/**
	 * Called when any {@link #handleRequest(Exchange)} or
	 * {@link #handleResponse(Exchange)} later in the chain returned
	 * {@link Outcome#ABORT} or threw an exception.
	 * 
	 * handleAbort is called in the reverse order of the chain (as
	 * handleResponse is).
	 */
	public void handleAbort(Exchange exchange);
	
	public String getDisplayName();
	public void setDisplayName(String name);
	
	public String getId();
	public void setId(String id);
	
	public Router getRouter();

	public void setFlow(EnumSet<Flow> flow);	
	public EnumSet<Flow> getFlow();
	
	public String getShortDescription();
	public String getLongDescription();
	
	/**
	 * @return "accessControl" if http://membrane-soa.org/esb-doc/current/configuration/reference/accessControl.htm is the documentation page
	 * for this interceptor, or null if there is no such page.
	 */
	public String getHelpId();
	
	public void init(Router router) throws Exception;
}
