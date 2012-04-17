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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.util.ByteUtil;

/**
 * Reads a normal input steam (no chunking) and writes chunks ("Transfer-Encoding: chunked").
 */
public class ChunkedOutBody extends ChunkedBody {

	private static final Log log = LogFactory.getLog(ChunkedOutBody.class.getName());
	private InputStream inputStream;
	
	public ChunkedOutBody(InputStream in) {
		log.debug("ChunkedInOutBody constructor");
		inputStream = in;
	}

	@Override
	protected void readLocal() throws IOException {
		chunks.add(new Chunk(ByteUtil.getByteArrayData(inputStream)));
	}

	protected void writeNotRead(AbstractBodyWriter out) throws IOException {
		log.debug("writeNotReadChunkedOut");
		
		byte[] buffer = new byte[8192];

		int length = 0;
		chunks.clear();
		while ((length = inputStream.read(buffer)) > 0) {
			byte[] chunk = new byte[length];
			System.arraycopy(buffer, 0, chunk, 0, length);
			Chunk c = new Chunk(chunk);
			chunks.add(c);

			out.write(c);
			
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
		return getContent();
	}
	
}
