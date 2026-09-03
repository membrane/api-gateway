/* Copyright 2009, 2010 predic8 GmbH, www.predic8.com

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

/**
 * What an interceptor tells the {@link FlowController} to do next. When to return which is
 * documented on {@link Interceptor}.
 */
public enum Outcome {

	/**
	 * Continue with the interceptor chain.
	 */
	CONTINUE,

	/**
	 * Do not continue the interceptor chain, but start normal response handling: flow is reversed and
	 * interceptors are invoked on the way back and given a chance to handle the response (in reverse order).
	 *
	 * Returned when the interceptor has answered the request itself and that is one of its
	 * regular results rather than a failure: a cache hit or a mock response. The response has to be set on the exchange.
	 */
	RETURN,

	/**
	 * Abort the interceptor chain, start abortion handling: the interceptors passed up to this
	 * point receive {@link Interceptor#handleAbort} instead of
	 * {@link Interceptor#handleResponse}.
	 *
	 * Returned when handling failed so fundamentally that the response flow has nothing
	 * reasonable left to do with the message, for example a body that could not be parsed or a
	 * backend that could not be reached. The response has to be set on the exchange.
	 */
	ABORT

}
