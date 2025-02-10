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

import org.slf4j.*;

import java.io.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.*;

/**
 * A HTTP message body (request or response), as it is received or constructed
 * internally by Membrane.
 * <p>
 * (Sending a body is handled by one of the {@link AbstractBodyTransferrer}s.)
 * <p>
 * To read a body, use the concrete implementation {@link ChunkedBody} (iff
 * "Transfer-Encoding: chunked" is used) or {@link Body} (iff not). To construct
 * a body within Membrane, {@link Body} is used by some helper method like
 * {@link Response.ResponseBuilder#body(String)}.
 * <p>
 * This class supports "streaming" the body: If a HTTP message is directly
 * forwarded by Membrane (without any component reading or changing the
 * message's body), the incoming network stream's buffer is directly written to
 * the output stream. This allows Membrane to perform very well in this
 * situation.
 * <p>
 * Repeatedly accessing the body (using streams or not) is supported. Body
 * Streams do not have to be read completely. Accessing the body from multiple
 * threads is illegal. Using a Body Stream after the Body as been accessed by
 * someone else (using streams or not) is illegal.
 */
public abstract class AbstractBody {
	private static final Logger log = LoggerFactory.getLogger(AbstractBody.class.getName());

	// whether the body has been read completely from the wire
	boolean read;

	protected List<Chunk> chunks = new ArrayList<>();
	protected List<MessageObserver> observers = new ArrayList<>(1);
	private boolean wasStreamed = false;

	public void read() throws IOException {
		if (read)
			return;

		if (wasStreamed)
			throw new IllegalStateException("Cannot read body after it was streamed.");

		for (MessageObserver observer : observers)
			observer.bodyRequested(this);

		chunks.clear();
		readLocal();
		markAsRead();
	}

	public void discard() throws IOException {
		read();
	}

	protected void markAsRead() {
		if (read)
			return;

		read = true;

		for (MessageObserver observer : observers)
			observer.bodyComplete(this);

		observers.clear();
	}

	protected abstract void readLocal() throws IOException;

	/**
	 * Returns the body's content as a byte[] representation.
	 * <p>
	 * For example, {@link #getContent()} might return a byte representation of
	 *
	 * <pre>
	 * Wikipedia in
	 *
	 * chunks.
	 * </pre>
	 *
	 * The return value does not differ whether "Transfer-Encoding: chunked" is
	 * used or not (see <a href="http://en.wikipedia.org/wiki/Chunked_transfer_encoding">Chunked Transfer Encoding</a>
	 * ), the example above is taken from there.
	 * <p>
	 * Please note that a new array is allocated when calling
	 * {@link #getContent()}. If you do not need the body as one single byte[],
	 * you should therefore use {@link #getContentAsStream()} instead.
	 */
	public byte[] getContent() throws IOException {
		read();
		byte[] content = new byte[getLength()];
		int destPos = 0;
		for (Chunk chunk : chunks) {
			destPos = chunk.copyChunk(content, destPos);
		}
		return content;
	}

	public InputStream getContentAsStream() throws IOException {
		read();
		return new BodyInputStream(chunks);
	}

	public void write(AbstractBodyTransferrer out, boolean retainCopy) throws IOException {
		if (!read && !retainCopy) {
			if (wasStreamed)
				log.warn("Streaming the body twice will not work.");
			for (MessageObserver observer : observers)
				observer.bodyRequested(this);
			wasStreamed = true;
			writeStreamed(out);
			return;
		}

		writeAlreadyRead(out);
	}

	protected abstract void writeAlreadyRead(AbstractBodyTransferrer out) throws IOException;

	protected abstract void writeNotRead(AbstractBodyTransferrer out) throws IOException;

	/**
	 * Is called when there are no observers that need to read the body. Streams the body without reading it
	 */
	protected abstract void writeStreamed(AbstractBodyTransferrer out) throws IOException;

	/**
	 * Warning: Calling this method will trigger reading the body from the client, disabling "streaming".
	 * Use {@link #isRead()} to determine wether the body already has been read, if necessary.
	 *
	 * @return the length of the return value of {@link #getContent()}
	 */
	public int getLength() throws IOException {
		read();

		int length = 0;
		for (Chunk chunk : chunks) {
			length += chunk.getLength();
		}
		return length;
	}

	/**
	 * Returns a reconstruction of the over-the-wire byte sequence received.
	 * <p>
	 * When "Transfer-Encoding: chunked" is used (see
	 * <a href="http://en.wikipedia.org/wiki/Chunked_transfer_encoding">Chunked Transfer Encoding</a> ), the return
	 * value might be (to follow the example from Wikipedia) a byte representation of
	 *
	 * <pre>
	 * 4
	 * Wiki
	 * 5
	 * pedia
	 * E
	 *  in
	 *
	 * chunks.
	 * 0
	 * </pre>
	 */
	public byte[] getRaw() throws IOException {
		read();
		return getRawLocal();
	}

	protected abstract byte[] getRawLocal() throws IOException;

	protected boolean hasRelevantObservers() {
		return observers.stream().anyMatch(o -> !(o instanceof NonRelevantBodyObserver));
	}

	/**
	 * Supposes UTF-8 encoding. Should therefore not be used
	 * for primary functionality.
	 */
	@Override
	public String toString() {
		if (chunks.isEmpty()) {
			return "";
		}
		try {
			return new String(getRaw(), UTF_8);
		} catch (IOException e) {
			log.error("", e);
			return "Error in body: " + e;
		}
	}

	public boolean isRead() {
		return read;
	}

	void addObserver(MessageObserver observer) {
		if (read) {
			observer.bodyComplete(this);
			return;
		}
		if (wasStreamed)
			log.warn("adding body observer after body was streamed.");
		observers.add(observer);
	}

	public List<MessageObserver> getObservers() {
		return observers;
	}

	public boolean wasStreamed() {
		return wasStreamed;
	}

	public Header getTrailer() {
		return null;
	}

	/**
	 * @return true, when the body supports trailers and the trailer was therefore set.
	 */
	public boolean setTrailer(Header trailer) {
		return false;
	}

	public boolean hasTrailer() {
		return false;
	}
}
