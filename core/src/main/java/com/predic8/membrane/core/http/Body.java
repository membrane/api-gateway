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

import com.predic8.membrane.core.util.ByteUtil;

/**
 * A message body (streaming, if possible). Use a subclass of {@link ChunkedBody} instead, if
 * "Transfer-Encoding: chunked" is set on the input.
 *
 * The "Transfer-Encoding" of the output is not determined by this class hierarchy, but by
 * {@link AbstractBodyTransferrer} and its subclasses.
 *
 * The caller is responsible to adjust the header accordingly,
 * e.g. the fields Transfer-Encoding and Content-Length.
 */
public class Body extends AbstractBody {

	private final static int BUFFER_SIZE;

	static {
		String bufferSize = System.getProperty("membrane.core.http.body.buffersize");
		BUFFER_SIZE = bufferSize == null ? 8192 : Integer.parseInt(bufferSize);
	}

	private static Log log = LogFactory.getLog(Body.class.getName());
	private final InputStream inputStream;
	private final int length;


	public Body(InputStream in) throws IOException {
		this(in, -1);
	}

	public Body(InputStream in, int length) throws IOException {
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
		chunks.add(new Chunk(ByteUtil.readByteArray(inputStream, length)));
	}

	@Override
	protected void writeAlreadyRead(AbstractBodyTransferrer out) throws IOException {
		if (getLength() == 0)
			return;

		out.write(getContent(), 0, getLength());
		out.finish();
	}

	@Override
	protected void writeNotRead(AbstractBodyTransferrer out) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];

		int totalLength = 0;
		int length = 0;
		chunks.clear();
		while ((this.length > totalLength || this.length == -1) && (length = inputStream.read(buffer)) > 0) {
			totalLength += length;
			out.write(buffer, 0, length);
			// TODO: this check is a temporary workaround only until non-replayable bodies have been figured out
			if (totalLength <= 100000000) {
				byte[] chunk = new byte[length];
				System.arraycopy(buffer, 0, chunk, 0, length);
				chunks.add(new Chunk(chunk));
			} else {
				chunks.add(new Chunk(new byte[0]) {
					@Override
					public byte[] getContent() {
						throw new IllegalStateException("Chunk too big to be retained.");
					}
					@Override
					public void write(OutputStream out) throws IOException {
						throw new IllegalStateException("Chunk too big to be retained.");
					}
					@Override
					public String toString() {
						throw new IllegalStateException("Chunk too big to be retained.");
					}
					@Override
					public int copyChunk(byte[] raw, int destPos) {
						throw new IllegalStateException("Chunk too big to be retained.");
					}
				});
			}
		}
		out.finish();
		markAsRead();
	}

	@Override
	protected byte[] getRawLocal() throws IOException {
		if (chunks.isEmpty()) {
			log.debug("size of chunks list: " + chunks.size() + "  " + hashCode());
			log.debug("chunks size is: " + chunks.size() + " at time: " + System.currentTimeMillis());
			return new byte[0];
		}

		return getContent();
	}
}
