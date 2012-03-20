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
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.HttpUtil;

public class ChunkedBody extends AbstractBody {

	private static final Log log = LogFactory.getLog(ChunkedBody.class.getName());
	private static final byte[] ZERO = "0".getBytes(Constants.UTF_8_CHARSET);
	
	public ChunkedBody(InputStream in) {
		log.debug("Chunked Body constructor");
		inputStream = in;
	}

	@Override
	protected void readLocal() throws IOException {
		chunks.addAll(HttpUtil.readChunks(inputStream));
	}

	@Override
	protected void writeAlreadyRead(OutputStream out) throws IOException {
		if (getLength() == 0)
			return;

		for (Chunk chunk : chunks) {
			chunk.write(out);
		}
		out.write(ZERO);
		out.write(Constants.CRLF_BYTES);
		out.write(Constants.CRLF_BYTES);
	}
	
	protected void writeNotRead(OutputStream out) throws IOException {
		log.debug("writeNotReadChunked");
		int chunkSize;
		while ((chunkSize = HttpUtil.readChunkSize(inputStream)) > 0) {
			writeChunkSize(out, chunkSize);
			byte[] chunk = ByteUtil.readByteArray(inputStream, chunkSize);
			out.write(chunk);
			chunks.add(new Chunk(chunk));
			out.write(Constants.CRLF_BYTES);
			inputStream.read(); // CR
			inputStream.read(); // LF
		}
		inputStream.read(); // CR
		inputStream.read(); // LF-
		writeLastChunk(out);
		read = true;
	}
	
	private void writeLastChunk(OutputStream out) throws IOException {
		out.write(ZERO);
		out.write(Constants.CRLF_BYTES);
		out.write(Constants.CRLF_BYTES);
	}

	private void writeChunkSize(OutputStream out, int chunkSize) throws IOException {
		out.write(Integer.toHexString(chunkSize).getBytes(Constants.UTF_8_CHARSET));
		out.write(Constants.CRLF_BYTES);
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
