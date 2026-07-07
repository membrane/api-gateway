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
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.http.Header.CONTENT_ENCODING;
import static com.predic8.membrane.core.http.Header.CONTENT_LENGTH;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Enforces a maximum body size, rejecting oversized messages with a Problem Details 400 response.
 * <p>
 * If a <code>Content-Length</code> header is already present and exceeds the limit, the message is
 * rejected immediately without reading the body. Otherwise body bytes are counted lazily as they
 * stream through; the limit is applied to the <b>decoded</b> (uncompressed) byte count, so
 * Content-Encoding compression (gzip, brotli, deflate) cannot be used to bypass it.
 * When decoding is applied, <code>Content-Encoding</code> and <code>Content-Length</code> are
 * removed from the forwarded message because the decoded size is not known ahead of time.
 * </p>
 * <p>
 * Due to Membrane's streaming architecture, request headers may already have been forwarded to
 * the backend when the limit is exceeded mid-body; in that case the backend connection is closed.
 * </p>
 * <p>See tutorials/security/100-SQL-Injection-Protection.yaml.</p>
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

		long len = msg.getHeader().getContentLength();
		if (len != -1 && len > maxBodyLength) {
			log.info("Message length of {} exceeded limit {}.",len,maxBodyLength);
			security(router.getConfiguration().isProduction(), getDisplayName())
					.title("Message is too large.")
					.detail("Message bodies must be smaller than limit.")
					.internal("maxBodyLength", maxBodyLength)
					.buildAndSetResponse(exc);
			return ABORT;
		}

		msg.setBody(new Body(new LengthLimitingStream(msg.getBodyAsStreamDecoded())));
		msg.getHeader().removeFields(CONTENT_ENCODING);
		msg.getHeader().removeFields(CONTENT_LENGTH);

		return CONTINUE;
	}

	public class LengthLimitingStream extends InputStream {

		private final InputStream is;

		private long pos;

		public LengthLimitingStream(InputStream is) {
			this.is = is;
		}

		private void checkPosition() throws IOException {
			if (pos > maxBodyLength) {
				log.info("Message length >= {} exceeded limit {}.",pos,maxBodyLength);
				throw new IOException("Message body too large.");
			}
		}

		@Override
		public int read() throws IOException {
			int i = is.read();
			if (i == -1)
				return i;
			pos++;
			checkPosition();
			return i;
		}

		@Override
		public int read(byte[] b) throws IOException {
			int l = is.read(b);
			if (l == -1)
				return l;
			pos += l;
			checkPosition();
			return l;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int l = is.read(b, off, len);
			if (l == -1)
				return l;
			pos += l;
			checkPosition();
			return l;
		}

		@Override
		public long skip(long n) throws IOException {
			long l = is.skip(n);
			if (l == -1) // Is always false
				return l;
			pos += l;
			checkPosition();
			return l;
		}

		@Override
		public int available() throws IOException {
			return is.available();
		}

		@Override
		public String toString() {
			return "LengthLimitingStream(" + is.toString() + ")";
		}

		@Override
		public void close() throws IOException {
			is.close();
		}
    }
}