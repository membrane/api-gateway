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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.router.Router;

import java.util.EnumSet;

/**
 * An interceptor can be put into the message flow. There it has access to the data flowing
 * through via the Exchange object. An interceptor can read but also manipulate messages. It can
 * also alter the flow of messages.
 *
 * Interceptor implementations need to be thread safe.
 *
 * The three flows
 *
 * An exchange passes a chain of interceptors up to three times, driven by the
 * {@link FlowController}:
 *
 * - Request flow: {@link #handleRequest(Exchange)} is called from the first interceptor of the
 *   chain towards the last, until one of them does not return {@link Outcome#CONTINUE}.
 * - Response flow: {@link #handleResponse(Exchange)} is called on the interceptors already
 *   passed, in reverse order.
 * - Abort flow: {@link #handleAbort(Exchange)} is called instead of
 *   {@link #handleResponse(Exchange)}, also in reverse order.
 *
 * An interceptor takes part in the request and in the response flow only if it declares them
 * through {@link #setAppliedFlow(EnumSet)}. The abort flow does not check that declaration, see
 * {@link #handleAbort(Exchange)}.
 *
 * Ending the request flow: RETURN or ABORT
 *
 * {@link Outcome#RETURN} and {@link Outcome#ABORT} both stop the request flow and send the
 * response that the interceptor has set on the exchange. They differ in the reason, and
 * therefore in whether the interceptors already passed still get to work on that response.
 *
 * RETURN means a normal result, produced early. The interceptor has answered the request itself
 * instead of letting it travel on: a cache serving a hit or a mock or static response.
 * Nothing is broken here; the response is one
 * of this interceptor's regular outcomes, only reached by a short circuit. The response flow
 * runs as usual, so logging, header rewriting or metrics on the way back still see the message
 * and can still change it.
 *
 * ABORT means there is nothing reasonable left to do on the way back. Handling failed so
 * fundamentally that no interceptor on the way back could do anything sensible with the message:
 * the body could not be parsed, the message structure is invalid, a required backend could not
 * be reached. Running the response flow over such a message would be pointless or even
 * misleading, so it is skipped: every interceptor already passed gets
 * {@link #handleAbort(Exchange)} instead. Aborting is a one-way street, the chain keeps
 * unwinding no matter what those interceptors do, but they still see the exchange and may
 * clean up, record the failure and adjust the error response on it. An exception escaping
 * {@link #handleRequest(Exchange)} has the same effect, the {@link FlowController} turns it
 * into an abort with an error response of its own.
 *
 * Rule of thumb: if the interceptors on the way back should do something useful with the
 * response, return RETURN. Else, return ABORT.
 */
public interface Interceptor {

	/**
	 * The three flows an interceptor can take part in. Note that {@link #ABORT} names the flow
	 * that runs {@link Interceptor#handleAbort(Exchange)} on the way back, while
	 * {@link Outcome#ABORT} is what an interceptor returns to start that flow.
	 */
	enum Flow {
		REQUEST, RESPONSE, ABORT;

		/**
		 * The flow combinations interceptors commonly declare through
		 * {@link Interceptor#setAppliedFlow(EnumSet)}.
		 */
		public static class Set {
			public static final EnumSet<Flow> REQUEST_FLOW = EnumSet.of(Flow.REQUEST);
			public static final EnumSet<Flow> RESPONSE_FLOW = EnumSet.of(Flow.RESPONSE);
			public static final EnumSet<Flow> REQUEST_RESPONSE_FLOW = EnumSet.of(Flow.REQUEST, Flow.RESPONSE);
			public static final EnumSet<Flow> RESPONSE_ABORT_FLOW = EnumSet.of(Flow.RESPONSE, Flow.ABORT);
			public static final EnumSet<Flow> REQUEST_RESPONSE_ABORT_FLOW = EnumSet.of(Flow.REQUEST, Flow.RESPONSE, Flow.ABORT);
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

	/**
	 * Called once after the configuration has been parsed and this interceptor has been added to
	 * the object tree below the {@link Router}, before any exchange is handled. Configuration
	 * validation and everything derived from the configuration belongs here rather than into the
	 * constructor, where the attributes are not set yet. Implementations extending
	 * {@link AbstractInterceptor} override its no-argument init() instead of this method.
	 *
	 * @param router the router this interceptor belongs to
	 */
	void init(Router router);

	/**
	 * Like {@link #init(Router)}, but also passes the {@link Proxy} the interceptor is configured
	 * in. Container interceptors pass their own proxy on to their children, so nested
	 * interceptors are initialized with the proxy they are nested in.
	 *
	 * @param router the router this interceptor belongs to
	 * @param proxy  the proxy this interceptor is attached to
	 */
	void init(Router router, Proxy proxy);

	/**
	 * Called while the request travels from the client towards the backend, in the order the
	 * interceptors are configured.
	 *
	 * @param exchange the exchange being handled; the request is {@link Exchange#getRequest()}
	 * @return {@link Outcome#CONTINUE} to hand the exchange to the next interceptor,
	 *         {@link Outcome#RETURN} to answer the request from here, or {@link Outcome#ABORT} to
	 *         give up on it. RETURN and ABORT both require a response to be set on the exchange;
	 *         see the interface documentation for which of the two to use.
	 */
	Outcome handleRequest(Exchange exchange);

	/**
	 * Called while the response travels from the backend back to the client, in the reverse order
	 * of the request flow. Only interceptors that were passed in the request flow and that
	 * declared {@link Flow#RESPONSE} are called; the interceptor that ended the request flow with
	 * {@link Outcome#RETURN} is not called again on the way back.
	 *
	 * @param exchange the exchange being handled; the response is {@link Exchange#getResponse()}
	 * @return {@link Outcome#CONTINUE} to continue with the next interceptor, or
	 *         {@link Outcome#ABORT} to abort the response flow, which makes the remaining
	 *         interceptors receive {@link #handleAbort(Exchange)} instead of
	 *         {@link #handleResponse(Exchange)}. {@link Outcome#RETURN} has no meaning in the
	 *         response flow.
	 */
	Outcome handleResponse(Exchange exchange);

	/**
	 * Called instead of {@link #handleResponse(Exchange)} once handling of the exchange has been
	 * given up: an interceptor later in the request flow returned {@link Outcome#ABORT} or threw
	 * an exception, or an interceptor in the response flow returned {@link Outcome#ABORT}.
	 *
	 * handleAbort is called in the reverse order of the chain (as handleResponse is) and never on
	 * the interceptor that aborted, because both flows reverse at the position before it. It is
	 * not limited to the interceptors that actually ran, though: the abort walk checks neither the
	 * applied flow nor {@link #handlesRequests()}, and a chain-owning interceptor such as the
	 * UserFeatureInterceptor forwards handleAbort to its whole sub-chain. An interceptor that the
	 * request flow skipped can therefore be called here, which is why implementations have to
	 * cope with a missing counterpart in {@link #handleRequest(Exchange)} and with a response
	 * that is not set yet.
	 *
	 * Having no return value, handleAbort cannot stop or redirect the unwinding: the abort runs to
	 * the end in any case. It does work on the exchange, though. Typical uses are releasing what
	 * {@link #handleRequest(Exchange)} acquired, such as connections, permits or temporary files,
	 * and logging or measuring the failure, but the error response can be changed here as well:
	 * the headerFilter interceptor filters the headers of the aborted response, and the abort
	 * element runs its children over the exchange. When the abort was caused by an exception, that
	 * exception is available as the exchange property {@link FlowController#ABORTION_REASON}. An
	 * exception thrown here is logged and ignored; the abort continues either way.
	 *
	 * @param exchange the aborted exchange, usually carrying the error response
	 */
	void handleAbort(Exchange exchange);

	/**
	 * Name of this interceptor in log messages and in the admin console. Defaults to the name of
	 * the implementing class.
	 */
	String getDisplayName();

	void setDisplayName(String name);

	/**
	 * The router this interceptor was initialized with.
	 */
	Router getRouter();

	/**
	 * The proxy where this interceptor is attached to, no matter how deep in
	 * nested interceptors it is.
	 */
	Proxy getProxy();

	/**
	 * Declares which of the three flows this interceptor takes part in; usually called by the
	 * implementation's constructor with one of the constants in {@link Flow.Set}.
	 *
	 * For REQUEST and RESPONSE this is more than a description: {@link #handlesRequests()} and
	 * {@link #handlesResponses()} report what was declared here, and the {@link FlowController}
	 * skips an interceptor in a flow it does not handle. Declaring REQUEST only is what makes the
	 * request element run its children on the way in and never on the way back. ABORT, on the
	 * other hand, is not checked by the FlowController at all, see {@link #handleAbort(Exchange)}.
	 *
	 * In {@link AbstractInterceptor} the two handles methods read the set passed to this method
	 * rather than {@link #getAppliedFlow()}. Overriding the getter alone therefore has no effect
	 * on the flow, it only changes what the admin console displays.
	 */
	void setAppliedFlow(EnumSet<Flow> flow);

	/**
	 * The flows this interceptor takes part in, see {@link #setAppliedFlow(EnumSet)}.
	 */
	EnumSet<Flow> getAppliedFlow();

	/**
	 * If interceptor can handle messages in the request flow. If false, the
	 * {@link FlowController} skips this interceptor in the request flow.
	 */
	boolean handlesRequests();

	/**
	 * If interceptor can handle messages in the response flow. If false, the
	 * {@link FlowController} skips this interceptor in the response flow.
	 */
	boolean handlesResponses();

	/**
	 * One sentence on what this interceptor does with its current configuration, shown in the
	 * admin console. The returned text is rendered as HTML.
	 */
	String getShortDescription();

	/**
	 * The long form of {@link #getShortDescription()} for the interceptor's detail page in the
	 * admin console, defaulting to the short description. The returned text is rendered as HTML.
	 */
	String getLongDescription();

	/**
	 * @return "accessControl" if
	 *         https://membrane-soa.org/service-proxy-doc/current/configuration/reference/accessControl.htm
	 *         is the documentation page for this interceptor, or null if there is no such page.
	 */
	String getHelpId();


}
