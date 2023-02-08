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

package com.predic8.membrane.core.http;

import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.io.*;
import java.nio.charset.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;

/**
 * A HTTP message (request or response).
 */
public abstract class Message {

	private static final Logger log = LoggerFactory.getLogger(Message.class.getName());

	private static final XOPReconstitutor xopr = new com.predic8.membrane.core.multipart.XOPReconstitutor();

	protected Header header;

	protected AbstractBody body;

	protected String version = "1.1";

	private boolean released = false;

	private String errorMessage = "";

	public Message() {
		header = new Header();
		body = new EmptyBody();

	}

	/**
	 * If the message is HTTP 1.1 but the header has no information about the
	 * content length, then an assumption is made that after the body the server
	 * will send an EOF. So the body is read till end of the stream.
	 *
	 * See <a href="http://www.ietf.org/rfc/rfc2145.txt">http://www.ietf.org/rfc/rfc2145.txt</a>
	 */
	public void read(InputStream in, boolean createBody) throws IOException, EndOfStreamException {
		parseStartLine(in);
		header = new Header(in);

		if (createBody)
			createBody(in);
	}

	public void readBody() throws IOException {
		body.read();
	}

	public void discardBody() throws IOException {
		body.discard();
	}

	public AbstractBody getBody() {
		return body;
	}

	/**
	 * <p>Probably you want to use {@link #getBodyAsStreamDecoded()} instead:</p>
	 *
	 * <p>Transfer-Encodings (e.g. chunking) have been unapplied, but Content-Encodings (e.g. gzip) have <b>not</b>.</p>
	 *
	 * <p>Returns the body as a stream.</p>
	 *
	 * <p>Supports streaming: The HTTP message does not have to be completely received yet for this method to return.</p>
	 *
	 * @see AbstractBody#getContentAsStream()
	 */
	public InputStream getBodyAsStream() {
		try {
			return body.getContentAsStream();
		} catch (IOException e) {
			log.error("Could not get body as stream", e);
			throw new RuntimeException("Could not get body as stream", e);
		}
	}

	/**
	 * <p>Returns the logical body content.</p>
	 *
	 * <p>Any Transfer-Encodings (e.g. chunking) and/or Content-Encodings (e.g. gzip) have been unapplied.</p>
	 *
	 * <p>Supports streaming: The HTTP message does not have to be completely received yet for this method to return.</p>
	 */
	public InputStream getBodyAsStreamDecoded() {
		// TODO: this logic should be split up into configurable decoding modules
		// TODO: decoding result should be cached
		try {
			Message m = xopr.getReconstitutedMessage(this);
			if (m != null)
				return m.getBodyAsStream(); // we know decoding is not necessary any more
			return MessageUtil.getContentAsStream(this);
		} catch (Exception e) {
			log.error("Could not decode body stream", e);
			throw new RuntimeException("Could not decode body stream", e);
		}
	}

	/**
	 * <p>As this method has bad performance, it should <b>not</b> be used in any critical component.
	 * (Use {@link #getBodyAsStreamDecoded()} instead.)</p>
	 *
	 * <p>Allocates a new {@link String} object for the whole body (potentially performing charset conversion).</p>
	 *
	 * <p>Blocks until the body has been fully received.</p>
	 *
	 * <p>(... and more bad internal performance)</p>
	 *
	 * @return the message's body as a Java String.
	 */
	public String getBodyAsStringDecoded() {
		try {
			return new String(MessageUtil.getContent(this), getCharset());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Sets the body.
	 *
	 * Does <b>NOT</b> adjust the header fields (<tt>Content-Length</tt> etc.): Use {@link #setBodyContent(byte[])} instead.
	 */
	public void setBody(AbstractBody b) {
		body = b;
	}

	/**
	 * Sets the body. Also adjusts the header fields (<tt>Content-Length</tt>, <tt>Content-Encoding</tt>, <tt>Transfer-Encoding</tt>).
	 */
	public void setBodyContent(byte[] content) {
		body = new Body(content);
		header.removeFields(CONTENT_ENCODING);
		header.removeFields(TRANSFER_ENCODING);
		header.setContentLength(content.length);
	}

	protected void createBody(InputStream in) throws IOException {
		log.debug("createBody");
		if (isHTTP10()) {
			body = new Body(in, header.getContentLength());
			return;
		}

		if (header.isChunked()) {
			body = new ChunkedBody(in);
			return;
		}

		if (!isKeepAlive()  || header.hasContentLength() || header.isProxyConnectionClose()) {
			body = new Body(in, header.getContentLength());
			return;
		}


		if (log.isDebugEnabled()) {
			log.error("Message has no content length: " + toString());
		}

		if (this instanceof Request && ((Request)this).isOPTIONSRequest()) {
			// OPTIONS without Transfer-Encoding and Content-Length has no body,
			// see http://www.ietf.org/rfc/rfc2616.txt section 9.2
			body = new EmptyBody();
			return;
		}

		// Message is HTTP 1.1 but the header has no information about the content length.
		// An assumption is made that after the body the server will send EOF. So the body is read till end of the stream
		// See http://www.ietf.org/rfc/rfc2145.txt
		body = new Body(in);
	}

	abstract protected void parseStartLine(InputStream in) throws IOException, EndOfStreamException;

	public Header getHeader() {
		return header;
	}

	/**
	 *preserve synchronized keyword, notify method is called
	 */
	public synchronized void release() {
		notify();
		released = true;
	}

	public boolean hasMsgReleased() {
		return released;
	}

	public void setHeader(Header srcHeader) {
		header = srcHeader;
	}

	public final void write(OutputStream out, boolean retainBody) throws IOException {
		writeStartLine(out);
		header.write(out);
		out.write(CRLF_BYTES);

		if (header.is100ContinueExpected()) {
			out.flush();
			return;
		}

		body.write(getHeader().isChunked() ? new ChunkedBodyTransferrer(out) : new PlainBodyTransferrer(out), retainBody);

		out.flush();
	}

	/**
	 * The start line supposedly only contains ASCII characters. But since
	 * {@link HttpUtil#readLine(InputStream)} converts the input byte-by-byte
	 * to char-by-char, we use ISO-8859-1 for output.
	 */
	public void writeStartLine(OutputStream out) throws IOException {
		out.write(getStartLine().getBytes(StandardCharsets.ISO_8859_1));
	}

	public abstract String getStartLine();

	public boolean isHTTP11() {
		return version.equalsIgnoreCase("1.1");
	}

	public boolean isHTTP10() {
		return version.equalsIgnoreCase("1.0");
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}


	@Override
	public String toString() {
		return getStartLine() + header.toString() + CRLF + body.toString();
	}

	public boolean isKeepAlive() {
		if (isHTTP10())
			return false;
		if (header.getConnection() == null)
			return true;
		if (header.isConnectionClose())
			return false;

		if (header.isProxyConnectionClose())
			return false;

		return true;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getName() {
		return "message";
	}

	public boolean isBodyEmpty() throws IOException {
		if (header.hasContentLength())
			return header.getContentLength() == 0;


		if (getBody().read)
			return getBody().getLength() == 0;

		return false;
	}


	public boolean isImage() {
		if (header.getContentType() == null)
			return false;
		return header.getContentType().contains("image");
	}

	public boolean isXML() {
		if (header.getContentType() == null)
			return false;
		return header.getContentType().toLowerCase().indexOf("xml") > 0;
	}

	public boolean isJSON() {
		if (header.getContentType() == null)
			return false;
		return header.getContentType().indexOf("json") > 0;
	}

	public boolean isHTML() {
		if (header.getContentType() == null)
			return false;
		return header.getContentType().indexOf("html") > 0;
	}

	public boolean isCSS() {
		if (header.getContentType() == null)
			return false;
		return header.getContentType().indexOf("css") > 0;
	}

	public boolean isJavaScript() {
		if (header.getContentType() == null)
			return false;
		return header.getContentType().indexOf("javascript") > 0;
	}

	public boolean isGzip() {
		return "gzip".equalsIgnoreCase(header.getContentEncoding());
	}

	public boolean isDeflate() {
		return "deflate".equalsIgnoreCase(header.getContentEncoding());
	}

	public String getCharset() {
		return header.getCharset();
	}

	public void addObserver(MessageObserver observer) {
		body.addObserver(observer);
	}

	public int estimateHeapSize() {
		try {
			return 100 +
					(header != null ? header.estimateHeapSize() : 0) +
					(body != null ? body.isRead() ? body.getLength() : 0 : 0) +
					(errorMessage != null ? 2*errorMessage.length() : 0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

    public abstract <T extends Message> T createSnapshot(Runnable bodyUpdatedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) throws Exception;

	public <T extends Message> T createMessageSnapshot(T result, Runnable bodyUpdatedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) {
		result.setHeader(new Header(this.getHeader()));
		result.setErrorMessage(this.getErrorMessage());
		result.setReleased(this.isReleased());

		addObserver(new SnapshottingObserver<>(strategy, limit, result, bodyUpdatedCallback));

		return result;
	}

	public boolean isReleased() {
		return released;
	}

	public void setReleased(boolean released) {
		this.released = released;
	}

    public boolean containsObserver(MessageObserver obs){
		return body.observers.contains(obs);
	}

	private static class SnapshottingObserver<T extends Message> extends BodyCollectingMessageObserver {
		private final T result;
		private final Runnable bodyUpdatedCallback;

		public SnapshottingObserver(Strategy strategy, long limit, T result, Runnable bodyUpdatedCallback) {
			super(strategy, limit);
			this.result = result;
			this.bodyUpdatedCallback = bodyUpdatedCallback;
		}

		@Override
		public void bodyRequested(AbstractBody body) {

		}

		@Override
		public void bodyComplete(AbstractBody body2) {
			try {
				result.setBody(getBody(body2));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try {
				bodyUpdatedCallback.run();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
