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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Limits the maximum length of a HTTP message body.
 * @explanation <p>
 *              Note that due to the streaming nature of Membrane, a request header may already have been passed on to
 *              the backend, when the condition "body.length &gt; X" becomes true. In this case, further processing is
 *              aborted and the connection to the backend is simply closed.
 *              </p>
 *              <p>
 *              To apply <tt>&lt;limit/&gt;</tt> only to either requests or responses, wrap it in a corresponding tag:
 *              <tt>&lt;request&gt;&lt;limit ... /&gt;&lt;/request&gt;</tt>.
 *              </p>
 * @topic 6. Security
 */
@MCElement(name="limit")
public class LimitInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(LimitInterceptor.class);

	private long maxBodyLength = -1;

	public LimitInterceptor() {
		name = "Limit Interceptor";
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
	 * @description The maximal length of a message body.
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
			exc.setResponse(createFailureResponse());
			return ABORT;
		}

		msg.setBody(new Body(new LengthLimitingStream(msg.getBodyAsStream())));

		return CONTINUE;
	}

	private Response createFailureResponse() {
		return ProblemDetails.security(router.isProduction())
				.title("Message is too large")
				.detail("Message bodies must be smaller than %s bytes.".formatted(maxBodyLength)).build();
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
			if (l == -1)
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