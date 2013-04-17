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

import com.predic8.membrane.core.Constants;

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

	boolean read;
	
	protected List<Chunk> chunks = new ArrayList<Chunk>();
	private List<MessageObserver> observers = new ArrayList<MessageObserver>(1);

	public void read() throws IOException {
		if (read)
			return;
		
		chunks.clear();
		readLocal();
		markAsRead();
	}
	
	protected void markAsRead() {
		if (read)
			return;
		read = true;
		for (MessageObserver observer : observers)
			observer.bodyComplete(this);
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
	 * you should therefore use {@link #getBodyAsStream()} instead.
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

	public InputStream getBodyAsStream() throws IOException {
		read();
		return new BodyInputStream(chunks);
	}

	public void write(AbstractBodyTransferrer out) throws IOException {
		if (!read) {
			writeNotRead(out);
			return;
		}

		writeAlreadyRead(out);
	}

	protected abstract void writeAlreadyRead(AbstractBodyTransferrer out) throws IOException;

	protected abstract void writeNotRead(AbstractBodyTransferrer out) throws IOException;
	
	public int getLength() throws IOException {
		read();

		int length = 0;
		for (Chunk chunk : chunks) {
			length += chunk.getLength();
		}
		return length;
	}

	protected int getRawLength() throws IOException {
		if (chunks.size() == 0)
			return 0;
		int length = getLength();
		for (Chunk chunk : chunks) {
			length += Long.toHexString(chunk.getLength()).getBytes(Constants.UTF_8_CHARSET).length;
			length += 2 * Constants.CRLF_BYTES.length;
		}
		length += "0".getBytes(Constants.UTF_8_CHARSET).length;
		length += 2 * Constants.CRLF_BYTES.length;
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
			e.printStackTrace();
			return "Error in body: " + e;
		}
	}

	public boolean isRead() {
		return read;
	}
	
	void addObserver(MessageObserver observer) {
		observers.add(observer);
		if (read)
			observer.bodyComplete(this);
	}
}
