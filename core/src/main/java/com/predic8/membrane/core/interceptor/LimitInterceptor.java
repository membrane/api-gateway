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
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.EmptyBody;
import com.predic8.membrane.core.http.LimitedBody;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.exceptions.ProblemDetails.tooLarge;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Enforces a maximum body size. The limit is applied to the <b>decoded</b> (uncompressed) byte
 * count, so Content-Encoding compression (gzip, brotli, deflate) cannot be used to bypass it.
 * <p>
 * If a <code>Content-Length</code> header is present and exceeds the limit, the message is rejected with a
 * Problem Details response without reading the body. An uncompressed body with a
 * <code>Content-Length</code> within the limit cannot grow beyond it, so it is passed on unchanged.
 * </p>
 * <p>
 * Any other body (chunked, compressed, or of unknown length) is passed on decoded and chunked, without
 * <code>Content-Encoding</code> and <code>Content-Length</code>, and is measured while it is forwarded, so
 * it is streamed rather than held in memory. If it turns out to be too large, the transfer is cut off
 * after the limit and the connection is closed. A request then gets a Problem Details response unless the
 * response has already begun, but the backend may already have received the headers and the first part of
 * the body. A response that is too large reaches the client truncated. A plugin after
 * <code>limit</code> that reads the body rejects an oversized message with a Problem Details response.
 * </p>
 * <p>
 * Multipart messages are measured and forwarded as decompressed MIME bytes, including headers, boundaries
 * and attachments. XOP/MTOM reassembly is left to downstream consumers.
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
		return handleMessage(exc, exc.getRequest());
	}

	@Override
	public Outcome handleResponse(Exchange exc) {
		return handleMessage(exc, exc.getResponse());
	}

	@Override
	public String getShortDescription() {
		return maxBodyLength == -1 ? "" : "Limit the length of message bodies to " + maxBodyLength + " bytes.";
	}

	public long getMaxBodyLength() {
		return maxBodyLength;
	}

	/**
	 * @description Maximum body size in bytes, measured against the decoded (uncompressed) content.
	 * Set to <code>-1</code> to disable the limit.
	 * @default -1
	 * @example 10485760
	 */
	@MCAttribute
	public void setMaxBodyLength(long maxBodyLength) {
		this.maxBodyLength = maxBodyLength;
	}

	private Outcome handleMessage(Exchange exc, Message msg) {
		if (maxBodyLength == -1)
			return CONTINUE;

		// Also bounds what reading a compressed body of known length holds in memory before decoding it
		final long len = msg.getHeader().getContentLength();
		if (len != -1 && len > maxBodyLength) {
			log.info("Message length of {} exceeded limit {}.", len, maxBodyLength);
			return reject(exc);
		}

		// Nothing to limit. Replacing the body would add a framing header, wrong for a HEAD response and
		// not allowed for a 204.
		if (msg.getBody() instanceof EmptyBody)
			return CONTINUE;

		// A plain body never reads beyond its declared length, so checking that length is enough. The
		// declared length of the body is checked, not the header: only the former bounds what is read.
		if (msg.getBody() instanceof Body body && body.getDeclaredLength() != -1 && msg.getHeader().getContentEncoding() == null) {
			if (body.getDeclaredLength() > maxBodyLength) {
				log.info("Message length of {} exceeded limit {}.", body.getDeclaredLength(), maxBodyLength);
				return reject(exc);
			}
			return CONTINUE;
		}

		// Limit decompression before any MIME parsing or XOP attachment buffering. Keeping the MIME
		// representation also keeps the forwarded bytes consistent with the multipart Content-Type.
		// HTTP/1.0 has no chunked coding: there, the body ends with the connection
		LimitedBody.install(msg, maxBodyLength, !msg.isHTTP10() && !exc.getRequest().isHTTP10());
		return CONTINUE;
	}

	private Outcome reject(Exchange exc) {
		tooLarge(router.getConfiguration().isProduction(), getDisplayName(), maxBodyLength).buildAndSetResponse(exc);
		return ABORT;
	}
}
