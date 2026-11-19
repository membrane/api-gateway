/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.BodyLengthCheck.TooLong;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.exceptions.BodyFailureProblems.bodyTooLong;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Enforces a maximum body size. A request over the limit is answered with 413, a response
 * over the limit is replaced by a 502. Both are Problem Details responses.
 * <p>
 * The body is read into memory before it is passed on, so a message over the limit is rejected before
 * any part of it is forwarded. Reading stops as soon as the limit is exceeded. A body of known length
 * without a <code>Content-Encoding</code> is checked against its length and passed on as it arrives.
 * </p>
 * <p>
 * A compressed body (gzip, deflate, brotli) must stay within the limit both as received and once
 * decoded, so compression cannot be used to bypass it. The body is forwarded as received, still
 * compressed. A body with a <code>Content-Encoding</code> that Membrane does not decode, or an
 * XOP/MTOM package, is limited by the bytes received.
 * </p>
 * <p>
 * Messages without content, such as responses to HEAD requests and 304 responses, are not checked.
 * </p>
 * <p>
 * A streamed response, such as server-sent events, is held back until it ends. To limit only the
 * requests of such an API, place <code>limit</code> in the request flow.
 * </p>
 * <p>See tutorials/security/60-SQL-Injection-Protection.yaml.</p>
 * @topic 3. Security and Validation
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - limit:
 *         maxBodyLength: 10485760
 * </code></pre>
 */
@MCElement(name="limit")
public class LimitInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(LimitInterceptor.class);

	private long maxBodyLength = -1;

	public LimitInterceptor() {
		name = "limit interceptor";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		return handleMessage(exc, exc.getRequest(), REQUEST);
	}

	@Override
	public Outcome handleResponse(Exchange exc) {
		return handleMessage(exc, exc.getResponse(), RESPONSE);
	}

	@Override
	public String getShortDescription() {
		return maxBodyLength == -1 ? "" : "Limit the length of message bodies to " + maxBodyLength + " bytes.";
	}

	public long getMaxBodyLength() {
		return maxBodyLength;
	}

	/**
	 * @description Maximum body size in bytes. A compressed body is checked both as received and once
	 * decoded. Bodies up to this size are held in memory while they pass.
	 * Set to <code>-1</code> to disable the limit.
	 * @default -1
	 * @example 10485760
	 */
	@MCAttribute
	public void setMaxBodyLength(long maxBodyLength) {
		this.maxBodyLength = maxBodyLength;
	}

	private Outcome handleMessage(Exchange exc, Message msg, Flow flow) {
		if (maxBodyLength == -1)
			return CONTINUE;

		if (!(msg.checkBodyLength(maxBodyLength) instanceof TooLong))
			return CONTINUE;

		log.info("The {} body is longer than the limit of {} bytes.", flow == REQUEST ? "request" : "response", maxBodyLength);
		bodyTooLong(router.getConfiguration().isProduction(), getDisplayName(), flow, maxBodyLength).buildAndSetResponse(exc);
		return ABORT;
	}
}
