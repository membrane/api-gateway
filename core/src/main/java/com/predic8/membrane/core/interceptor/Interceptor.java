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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;

import java.util.*;

/**
 * TODO describe in short what an interceptor is.
 * <p>
 * Interceptor implementations need to be thread safe.
 *
 */
public interface Interceptor {

	enum Flow {
		REQUEST, RESPONSE, ABORT;

		public static class Set {
			public static final EnumSet<Flow> REQUEST = EnumSet.of(Flow.REQUEST);
			public static final EnumSet<Flow> RESPONSE_ABORT = EnumSet.of(Flow.RESPONSE, Flow.ABORT);
			public static final EnumSet<Flow> REQUEST_RESPONSE_ABORT = EnumSet.of(Flow.REQUEST, Flow.RESPONSE, Flow.ABORT);
		}

		public boolean isRequest() {
			return this.equals(REQUEST);
		}

		public boolean isResponse() {
			return this.equals(RESPONSE);
		}

		public boolean isAbort() {
			return this.equals(ABORT);
		}
	}

	Outcome handleRequest(Exchange exchange) throws Exception;
	Outcome handleResponse(Exchange exchange) throws Exception;

	/**
	 * Called when any {@link #handleRequest(Exchange)} or
	 * {@link #handleResponse(Exchange)} later in the chain returned
	 * {@link Outcome#ABORT} or threw an exception.
	 * <p>
	 * handleAbort is called in the reverse order of the chain (as
	 * handleResponse is).
	 */
	void handleAbort(Exchange exchange);

	String getDisplayName();
	void setDisplayName(String name);

	Router getRouter();

	void setFlow(EnumSet<Flow> flow);
	EnumSet<Flow> getFlow();

	/**
	 * If interceptor can handle messages in the request flow.
	 */
	boolean handlesRequests();

	/**
	 * If interceptor can handle messages in the response flow.
	 */
	boolean handlesResponses();

	String getShortDescription();
	String getLongDescription();

	/**
     * @return "accessControl" if <a href="https://membrane-soa.org/service-proxy-doc/current/configuration/reference/accessControl.htm">https://membrane-soa.org/service-proxy-doc/current/configuration/reference/accessControl.htm</a> is the documentation page
     * for this interceptor, or null if there is no such page.
     */
	String getHelpId();

	void init(Router router) throws Exception;
}
