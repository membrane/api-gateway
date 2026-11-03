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
import com.predic8.membrane.core.http.EmptyBody;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Enforces a maximum body size, rejecting oversized messages with a Problem Details 400 response.
 * <p>
 * If a <code>Content-Length</code> header is already present and exceeds the limit, the message is
 * rejected immediately without reading the body. Otherwise the body is read into memory before the
 * message is passed on, so an oversized message is rejected before it is forwarded. The limit is
 * applied to the <b>decoded</b> (uncompressed) byte count, so Content-Encoding compression (gzip,
 * brotli, deflate) cannot be used to bypass it. The message is forwarded decoded, without
 * <code>Content-Encoding</code> and with a <code>Content-Length</code> that matches the decoded body.
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

	/**
	 * The body is checked by reading it into one array, and a JVM cannot allocate a larger one: beyond
	 * it, {@link InputStream#readAllBytes()} throws an {@link OutOfMemoryError} instead of the limit
	 * rejecting the message.
	 */
	private static final long MAX_BUFFERED_LENGTH = Integer.MAX_VALUE - 8;

	private long maxBodyLength = -1;

	public LimitInterceptor() {
		name = "limit interceptor";
	}

	@Override
	public void init() {
		super.init();
		if (maxBodyLength > MAX_BUFFERED_LENGTH)
			log.warn("maxBodyLength {} is larger than a body held in memory can be. Using {} instead.", maxBodyLength, MAX_BUFFERED_LENGTH);
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
	 * Bodies up to this size are held in memory while they are checked, so values above 2147483639
	 * (about 2 GiB), the largest block a JVM can allocate, act as 2147483639.
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
		if (len != -1 && len > effectiveMaxBodyLength()) {
			log.info("Message length of {} exceeded limit {}.",len,effectiveMaxBodyLength());
			return tooLarge(exc);
		}

		// Nothing to limit. Replacing the body would add "Content-Length: 0", wrong for a HEAD response
		// and not allowed for a 204.
		if (msg.getBody() instanceof EmptyBody)
			return CONTINUE;

		// Read to the end instead of replacing the body with a limiting stream over it: replacing the
		// body discards the old one, and a chunked body drops the chunks such a stream would still need.
		// Reading to the end first lets the discard drain only what the decoder left unread, e.g. the
		// last chunk after a deflate stream, so the connection stays in step.
		final byte[] content;
		try (final var limited = new LengthLimitingStream(msg.getBodyAsStreamDecoded())) {
			content = limited.readAllBytes();
		} catch (LimitExceededException e) {
			return tooLarge(exc);
		} catch (IOException e) {
			throw new ReadingBodyException(e, msg);
		}
		msg.setBodyContent(content);
		return CONTINUE;
	}

	private Outcome tooLarge(Exchange exc) {
		security(router.getConfiguration().isProduction(), getDisplayName())
				.title("Message is too large.")
				.detail("Message bodies must be smaller than limit.")
				.internal("maxBodyLength", effectiveMaxBodyLength())
				.buildAndSetResponse(exc);
		return ABORT;
	}

	private long effectiveMaxBodyLength() {
		return Math.min(maxBodyLength, MAX_BUFFERED_LENGTH);
	}

	/** Tells the limit being exceeded apart from the body failing to arrive. */
	private static final class LimitExceededException extends IOException {
		LimitExceededException() {
			super("Message body too large.");
		}
	}

	public class LengthLimitingStream extends InputStream {

		private final InputStream is;

		private long pos;

		public LengthLimitingStream(InputStream is) {
			this.is = is;
		}

		private void checkPosition() throws IOException {
			if (pos > effectiveMaxBodyLength()) {
				log.info("Message length >= {} exceeded limit {}.",pos,effectiveMaxBodyLength());
				throw new LimitExceededException();
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