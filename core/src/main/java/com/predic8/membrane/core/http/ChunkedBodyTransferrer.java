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
import java.io.OutputStream;

import com.predic8.membrane.core.Constants;

public class ChunkedBodyTransferrer extends AbstractBodyTransferrer {
	OutputStream out;

	public ChunkedBodyTransferrer(OutputStream out) {
		this.out = out;
	}

	@Override
	public void write(byte[] content, int i, int length) throws IOException {
		writeChunkSize(out, length);
		out.write(content, i, length);
		out.write(Constants.CRLF_BYTES);
		out.flush();
	}

	@Override
	public void write(Chunk chunk) throws IOException {
		chunk.write(out);
	}

	@Override
	public void finish(Header header) throws IOException {
		out.write(ZERO);
		out.write(Constants.CRLF_BYTES);
		if (header != null) {
			header.write(out);
		}
		out.write(Constants.CRLF_BYTES);
	}


	protected static final byte[] ZERO = "0".getBytes(Constants.UTF_8_CHARSET);

	protected static void writeChunkSize(OutputStream out, int chunkSize) throws IOException {
		out.write(Integer.toHexString(chunkSize).getBytes(Constants.UTF_8_CHARSET));
		out.write(Constants.CRLF_BYTES);
	}

}
