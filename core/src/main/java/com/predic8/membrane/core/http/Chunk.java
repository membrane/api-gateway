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

import com.predic8.membrane.core.*;

import java.io.*;

import static java.nio.charset.StandardCharsets.*;

/**
 * A chunk of a HTTP message body.
 * <p>
 * Also see <a href="http://en.wikipedia.org/wiki/Chunked_transfer_encoding">Chunked Transfer Encoding</a> .
 * <p>
 * Used (independently of whether "Transfer-Encoding: chunked" is actually
 * present) to store the message body's data.
 */
public class Chunk {

	private final byte[] content;

	public Chunk(byte[] content) {
		this.content = content;
	}

	public byte[] getContent() {
		return content;
	}

	public int getLength() {
		if (content == null ) {
			return 0;
		}
		return content.length;
	}

	public void write(OutputStream out) throws IOException {
		if (content == null || out == null)
			return;

		out.write(getLengthBytes());
		out.write(Constants.CRLF_BYTES);
		out.write(getContent(), 0, getLength());
		out.write(Constants.CRLF_BYTES);
		out.flush();
	}

	/**
	 * Supposes UTF-8 encoding. Should therefore not be used
	 * for primary functionality.
	 */
	@Override
	public String toString() {
		if (content == null)
			return "";
		return new String(content, UTF_8);
	}

	public int copyChunk(byte[] raw, int destPos) {
		System.arraycopy(content, 0, raw, destPos, getLength());
		return destPos + getLength();
	}

	public int copyChunkLength(byte[] raw, int destPos, AbstractBody body) {
		System.arraycopy(getLengthBytes(), 0, raw, destPos, getLengthBytes().length);
		return destPos + getLengthBytes().length;
	}

	private byte[] getLengthBytes() {
		return Long.toHexString(getLength()).getBytes(UTF_8);
	}

}
