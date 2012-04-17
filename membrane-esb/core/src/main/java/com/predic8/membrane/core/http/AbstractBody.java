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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.predic8.membrane.core.Constants;

public abstract class AbstractBody {

	boolean read;
	
	protected List<Chunk> chunks = new ArrayList<Chunk>();

	public void read() throws IOException {
		if (read)
			return;
		
		chunks.clear();
		readLocal();
		read = true;
	}
	
	protected abstract void readLocal() throws IOException;

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
		return new ByteArrayInputStream(getContent());
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
	
}
