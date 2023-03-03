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
 * Also see {@link InterceptorFlowController}.
 */
public enum Outcome {

	/**
	 * Continue with the interceptor chain.
	 */
	CONTINUE,

	/**
	 * Do not continue the interceptor chain, but start normal response
	 * handling:
	 * <p>
	 * All interceptors passed up to this point will be given a chance to handle
	 * the response (in reverse order).
	 */
	RETURN,

	/**
	 * Abort the interceptor chain, start abortion handling.
	 */
	ABORT

}
