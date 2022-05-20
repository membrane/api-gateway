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

import static com.predic8.membrane.core.http.ChunkedBodyTransferrer.ZERO;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.HttpUtil;

/**
 * Reads the body with "Transfer-Encoding: chunked".
 *
 * See {@link ChunkedBodyTransferrer} for writing a body using chunks.
 */
public class ChunkedBody extends AbstractBody {

	private static final Logger log = LoggerFactory.getLogger(ChunkedBody.class.getName());
	private InputStream inputStream;
	private long lengthStreamed;

	public ChunkedBody(InputStream in) {
		log.debug("ChunkedInOutBody constructor");
		inputStream = in;
	}

	@Override
	public void read() throws IOException {
		if (bodyObserved && !bodyComplete)
			ByteUtil.readStream(getContentAsStream());
		bodyObserved = true;
		super.read();
	}

	@Override
	public void write(AbstractBodyTransferrer out, boolean retainCopy) throws IOException {
		if (bodyObserved && !bodyComplete)
			ByteUtil.readStream(getContentAsStream());
		super.write(out, retainCopy);
	}

	@Override
	protected void markAsRead() {
		super.markAsRead();
		bodyComplete = true;
	}

	@Override
	protected void readLocal() throws IOException {
		List<Chunk> chunkList = HttpUtil.readChunks(inputStream);
		chunks.addAll(chunkList);
		for (Chunk chunk : chunkList)
			for (MessageObserver observer : observers)
				observer.bodyChunk(chunk);
	}

	@Override
	public void discard() throws IOException {
		if (read)
			return;
		if (wasStreamed())
			return;

		for (MessageObserver observer : observers)
			observer.bodyRequested(this);

		HttpUtil.readChunksAndDrop(inputStream, observers);
		markAsRead();
	}

	boolean bodyObserved = false;
	boolean bodyComplete = false;

	public InputStream getContentAsStream() throws IOException {
		read = true;

		if (!bodyObserved) {
			bodyObserved = true;
			for (MessageObserver observer : observers)
				observer.bodyRequested(this);
			chunks.clear();
		}

		return new BodyInputStream(chunks) {
			@Override
			protected Chunk readNextChunk() throws IOException {
				if (bodyComplete)
					return null;
				int chunkSize = HttpUtil.readChunkSize(inputStream);
				if (chunkSize > 0) {
					Chunk c = new Chunk(ByteUtil.readByteArray(inputStream, chunkSize));
					inputStream.read(); // CR
					inputStream.read(); // LF
					for (MessageObserver observer : observers)
						observer.bodyChunk(c);
					return c;
				} else {
					inputStream.read(); // CR
					inputStream.read(); // LF

					bodyComplete = true;

					for (MessageObserver observer : observers)
						observer.bodyComplete(ChunkedBody.this);
					observers.clear();

					return null;
				}
			}
		};
	}

	@Override
	protected void writeNotRead(AbstractBodyTransferrer out) throws IOException {
		log.debug("writeNotReadChunked");
		int chunkSize;
		while ((chunkSize = HttpUtil.readChunkSize(inputStream)) > 0) {
			Chunk chunk = new Chunk(ByteUtil.readByteArray(inputStream, chunkSize));
			out.write(chunk);
			chunks.add(chunk);
			for (MessageObserver observer : observers)
				observer.bodyChunk(chunk);
			inputStream.read(); // CR
			inputStream.read(); // LF
		}
		inputStream.read(); // CR
		inputStream.read(); // LF-
		out.finish();
		markAsRead();
	}

	@Override
	protected void writeStreamed(AbstractBodyTransferrer out) throws IOException {
		log.debug("writeStreamed");
		int chunkSize;
		while ((chunkSize = HttpUtil.readChunkSize(inputStream)) > 0) {
			Chunk chunk = new Chunk(ByteUtil.readByteArray(inputStream, chunkSize));
			out.write(chunk);
			for (MessageObserver observer : observers)
				observer.bodyChunk(chunk);
			inputStream.read(); // CR
			inputStream.read(); // LF
			lengthStreamed += chunkSize;
		}
		inputStream.read(); // CR
		inputStream.read(); // LF-
		out.finish();
		markAsRead();
	}

	protected int getRawLength() throws IOException {
		if (chunks.isEmpty())
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

	@Override
	protected byte[] getRawLocal() throws IOException {
		byte[] raw = new byte[getRawLength()];
		int destPos = 0;
		for (Chunk chunk : chunks) {
			destPos = chunk.copyChunkLength(raw, destPos, this);
			destPos = copyCRLF(raw, destPos);
			destPos = chunk.copyChunk(raw, destPos);
			destPos = copyCRLF(raw, destPos);
		}
		destPos = copyLastChunk(raw, destPos);
		destPos = copyCRLF(raw, destPos);
		return raw;
	}

	private int copyLastChunk(byte[] raw, int destPos) {
		System.arraycopy(ZERO, 0, raw, destPos, ZERO.length);
		destPos += ZERO.length;
		destPos = copyCRLF(raw, destPos);
		return destPos;
	}

	private int copyCRLF(byte[] raw, int destPos) {
		System.arraycopy(Constants.CRLF_BYTES, 0, raw, destPos, 2);
		return destPos += 2;
	}

	@Override
	protected void writeAlreadyRead(AbstractBodyTransferrer out) throws IOException {
		if (getLength() == 0)
			return;

		for (Chunk chunk : chunks) {
			out.write(chunk);
		}
		out.finish();
	}

	@Override
	public int getLength() throws IOException {
		if (wasStreamed())
			return (int)lengthStreamed; // TODO: refactor into long
		return super.getLength();
	}

	@Override
	public boolean isRead() {
		return super.isRead() && bodyComplete;
	}
}
