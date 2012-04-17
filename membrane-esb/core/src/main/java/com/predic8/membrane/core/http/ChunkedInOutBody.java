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

import static com.predic8.membrane.core.http.ChunkedBodyWriter.ZERO;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.HttpUtil;

/**
 * Reads and writes the body with "Transfer-Encoding: chunked".
 */
public class ChunkedInOutBody extends ChunkedBody {

	private static final Log log = LogFactory.getLog(ChunkedInOutBody.class.getName());
	private InputStream inputStream;
	
	public ChunkedInOutBody(InputStream in) {
		log.debug("ChunkedInOutBody constructor");
		inputStream = in;
	}

	@Override
	protected void readLocal() throws IOException {
		chunks.addAll(HttpUtil.readChunks(inputStream));
	}

	protected void writeNotRead(AbstractBodyWriter out) throws IOException {
		log.debug("writeNotReadChunked");
		int chunkSize;
		while ((chunkSize = HttpUtil.readChunkSize(inputStream)) > 0) {
			Chunk chunk = new Chunk(ByteUtil.readByteArray(inputStream, chunkSize));
			out.write(chunk);
			chunks.add(chunk);
			inputStream.read(); // CR
			inputStream.read(); // LF
		}
		inputStream.read(); // CR
		inputStream.read(); // LF-
		out.writeLastChunk();
		read = true;
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

}
