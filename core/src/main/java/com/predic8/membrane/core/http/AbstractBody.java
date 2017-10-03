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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.predic8.membrane.core.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A HTTP message body (request or response), as it is received or constructed
 * internally by Membrane.
 *
 * (Sending a body is handled by one of the {@link AbstractBodyTransferrer}s.)
 *
 * To read a body, use the concrete implementation {@link ChunkedBody} (iff
 * "Transfer-Encoding: chunked" is used) or {@link Body} (iff not). To construct
 * a body within Membrane, {@link Body} is used by some helper method like
 * {@link Response.ResponseBuilder#body(String)}.
 *
 * This class supports "streaming" the body: If a HTTP message is directly
 * forwarded by Membrane (without any component reading or changing the
 * message's body), the incoming network stream's buffer is directly written to
 * the output stream. This allows Membrane to perform very well in this
 * situation.
 */
public abstract class AbstractBody {
	private static final Logger log = LoggerFactory.getLogger(AbstractBody.class.getName());

	boolean read;

	protected List<Chunk> chunks = new ArrayList<Chunk>();
	protected List<MessageObserver> observers = new ArrayList<MessageObserver>(1);
	private boolean wasStreamed = false;

	public void read() throws IOException {
		if (read)
			return;

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
	 * Returns the body's content as a byte[] represenatation.
	 *
	 * For example, {@link #getContent()} might return a byte representation of
	 *
	 * <pre>
	 * Wikipedia in
	 *
	 * chunks.
	 * </pre>
	 *
	 * The return value does not differ whether "Transfer-Encoding: chunked" is
	 * used or not (see http://en.wikipedia.org/wiki/Chunked_transfer_encoding
	 * ), the example above is taken from there.
	 *
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

	public void write(AbstractBodyTransferrer out) throws IOException {
		if (!read) {
			boolean relevantObservers = false;
			for(MessageObserver obs : observers)
				if(!(obs instanceof NonRelevantBodyObserver))
					relevantObservers = true;
			if(relevantObservers) {
				for (MessageObserver observer : observers)
					observer.bodyRequested(this);

				writeNotRead(out);
			}else {
				writeStreamed(out);
				wasStreamed = true;
			}
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
	 *
	 * When "Transfer-Encoding: chunked" is used (see
	 * http://en.wikipedia.org/wiki/Chunked_transfer_encoding ), the return
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
		//return observers.stream().filter(messageObserver -> !(messageObserver instanceof NonRelevantBodyObserver)).collect(Collectors.toList()).size() > 0;
                boolean hasRelevant = false;
                for (MessageObserver o: observers)
                {
                    if ( ! (o instanceof NonRelevantBodyObserver))
                    {
                        hasRelevant = true;
                        break;
                    }
                }
                return hasRelevant;
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
			return new String(getRaw(), Constants.UTF_8_CHARSET);
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
		observers.add(observer);
	}

	public List<MessageObserver> getObservers() {
		return observers;
	}

	public boolean wasStreamed() {
		return wasStreamed;
	}

	public void setWasStreamed(boolean wasStreamed) {
		this.wasStreamed = wasStreamed;
	}
}
