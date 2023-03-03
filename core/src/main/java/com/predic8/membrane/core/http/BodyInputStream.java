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
package com.predic8.membrane.core.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * An {@link InputStream} offering a <code>List&lt;Chunk&gt;</code> as one
 * single stream.
 * <p>
 * Used by {@link AbstractBody} to offer an efficient way of reading a message's
 * body.
 */
public class BodyInputStream extends InputStream {

	// the data
	private final List<Chunk> chunks;

	// position
	private int currentChunkIndex = 0;
	private int positionWithinChunk = -1;

	// cached data at position
	private Chunk currentChunk;
	private byte[] currentChunkData;
	private int currentChunkLength;

	public BodyInputStream(List<Chunk> chunks) {
		this.chunks = chunks;
		currentChunk = chunks.isEmpty() ? null : chunks.get(0);
		if (currentChunk != null) {
			currentChunkLength = currentChunk.getLength();
			currentChunkData = currentChunk.getContent();
		} else {
			currentChunkIndex = -1;
		}
	}

	/**
	 * @return whether the new position is still a valid index
	 */
	private boolean advanceToNextPosition() throws IOException {
		if (currentChunk == null) {
			currentChunk = readNextChunk();
			if (currentChunk == null) {
				return false;
			}
			chunks.add(currentChunk);
			currentChunkIndex = chunks.size() - 1;
			currentChunkLength = currentChunk.getLength();
			currentChunkData = currentChunk.getContent();
		}

		positionWithinChunk++;

		while (positionWithinChunk == currentChunkLength) {
			currentChunkIndex++;
			if (currentChunkIndex == chunks.size()) {
				Chunk c = readNextChunk();
				if (c == null) {
					currentChunk = null;
					return false;
				}
				chunks.add(c);
			}
			currentChunk = chunks.get(currentChunkIndex);
			currentChunkLength = currentChunk.getLength();
			currentChunkData = currentChunk.getContent();
			positionWithinChunk = 0;
		}
		return true;
	}

	/**
	 * @return the next chunk or null, if there is no next chunk
	 */
	protected Chunk readNextChunk() throws IOException {
		return null;
	}

	@Override
	public int read() throws IOException {
		if (!advanceToNextPosition())
			return -1;
		return currentChunkData[positionWithinChunk] & 0xFF;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (b == null) {
			throw new NullPointerException();
		} else if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		} else if (len == 0) {
			return 0;
		}

		if (!advanceToNextPosition())
			return -1;

		// read at most the rest of the current chunk
		if (len > currentChunkLength - positionWithinChunk)
			len = currentChunkLength - positionWithinChunk;

		System.arraycopy(currentChunkData, positionWithinChunk, b, off, len);
		positionWithinChunk += len - 1;
		return len;
	}

}
