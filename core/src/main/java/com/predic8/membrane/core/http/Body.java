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

import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.io.*;

import static java.lang.System.currentTimeMillis;

/**
 * A message body (streaming, if possible). Use a subclass of {@link ChunkedBody} instead, if
 * "Transfer-Encoding: chunked" is set on the input.
 * <p>
 * The "Transfer-Encoding" of the output is not determined by this class hierarchy, but by
 * {@link AbstractBodyTransferrer} and its subclasses.
 * <p>
 * The caller is responsible to adjust the header accordingly,
 * e.g. the fields Transfer-Encoding and Content-Length.
 * <p>
 * This class internally has a binary model whether the body is read or not.
 */
public class Body extends AbstractBody {

	private static final Logger log = LoggerFactory.getLogger(Body.class.getName());

	private final static int BUFFER_SIZE;
	private final static int MAX_CHUNK_LENGTH;

	static {
		String bufferSize = System.getProperty("membrane.core.http.body.buffersize");
		BUFFER_SIZE = bufferSize == null ? 8192 : Integer.parseInt(bufferSize);
		String maxChunkLength = System.getProperty("membrane.core.http.body.maxchunklength");
		MAX_CHUNK_LENGTH = maxChunkLength == null ? 1_000_000_000 : Integer.parseInt(maxChunkLength);
	}

	private final InputStream inputStream;
	private final long length;
	private long streamedLength;

	public Body(InputStream in) {
		this(in, -1);
	}

	public Body(InputStream in, long length) {
		this.inputStream = in;
		this.length = length;
	}

	public Body(byte[] content) {
		this.inputStream = null;
		this.length = content.length;
		chunks.clear();
		chunks.add(new Chunk(content));
		markAsRead(); // because we do not have something to read
	}

	@Override
	protected void readLocal() throws IOException {
		long l = length;
		while (l > 0 || l == -1) {
			int chunkLength = l > MAX_CHUNK_LENGTH ? MAX_CHUNK_LENGTH : (int)l;
			Chunk chunk = new Chunk(ByteUtil.readByteArray(inputStream, chunkLength));
			chunks.add(chunk);
			for (MessageObserver observer : observers)
				observer.bodyChunk(chunk);
			l -= chunkLength;
		}
	}

	public void discard() throws IOException {
		if (read)
			return;
		if (wasStreamed())
			return;

		for (MessageObserver observer : observers)
			observer.bodyRequested(this);

		skipBodyContent();
	}

	private void skipBodyContent() throws IOException {
		byte[] buffer = null;
		boolean hasRelevantObserver = hasRelevantObservers();
		if (hasRelevantObserver)
			buffer = new byte[BUFFER_SIZE];

		chunks.clear();
		long toSkip = length;
		while (toSkip > 0) {
			long skipped;
			if (hasRelevantObserver) {
				skipped = inputStream.read(buffer);
				if (skipped > 0)
					for (MessageObserver observer : observers)
						observer.bodyChunk(buffer, 0, (int)skipped);
			} else {
				skipped = inputStream.skip(toSkip);
			}
			if (skipped <= 0)
				break; // EOF
			toSkip -= skipped;
		}
		markAsRead();
	}

	@Override
	protected void writeAlreadyRead(AbstractBodyTransferrer out) throws IOException {
		if (getLength() > 0)
			out.write(getContent(), 0, getLength());
		out.finish(null);
	}

	@Override
	protected void writeNotRead(AbstractBodyTransferrer out) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];

		long totalLength = 0;
		int length;
		chunks.clear();
		while ((this.length > totalLength || this.length == -1) && (length = inputStream.read(buffer)) > 0) {
			totalLength += length;
			out.write(buffer, 0, length);
			byte[] chunk = new byte[length];
			System.arraycopy(buffer, 0, chunk, 0, length);
			Chunk chunk1 = new Chunk(chunk);
			chunks.add(chunk1);
			for (MessageObserver observer : observers)
				observer.bodyChunk(chunk1);
		}

		out.finish(null);
		markAsRead();
	}

	@Override
	protected void writeStreamed(AbstractBodyTransferrer out) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];

		long totalLength = 0;
		int length;
		chunks.clear();
		while ((this.length > totalLength || this.length == -1) && (length = inputStream.read(buffer)) > 0) {
			totalLength += length;
			streamedLength += length;
			out.write(buffer, 0, length);
			for (MessageObserver observer : observers)
				observer.bodyChunk(buffer, 0, length);
		}
		out.finish(null);
		markAsRead();
	}

	@Override
	public int getLength() throws IOException {
		if (wasStreamed())
			return (int)streamedLength;
		return super.getLength();
	}

	@Override
	protected byte[] getRawLocal() throws IOException {
		if (chunks.isEmpty()) {
			log.debug("Chunks list is empty: {}", hashCode());
			log.debug("At time: {}", currentTimeMillis());
			return new byte[0];
		}
		return getContent();
	}
}
