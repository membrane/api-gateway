/* Copyright 2005-2010 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.nio;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import junit.framework.TestCase;

/**
 * 
 */
public class NioTestBase extends TestCase {

	FileChannel requestData;

	public void loadData(String path) throws IOException {
		requestData = new FileInputStream(this.getClass().getResource(path)
				.getFile()).getChannel();
	}

	public void tearDown() throws Exception {
		requestData.close();
	}

	public int getDataLength() throws IOException {
		return (int) requestData.size();
	}

	public void rewind() throws IOException {
		requestData.position(0);
	}

	public ByteBuffer read(int size) throws IOException {
		if (requestData.position() >= requestData.size())
			throw new IOException("no more data to read.");
		ByteBuffer ret = ByteBuffer.allocate(size);
		requestData.read(ret);
		ret.flip();
		return ret;
	}

	public ByteBuffer[] readMultiple(int... sizes) throws IOException {
		ByteBuffer[] ret = new ByteBuffer[sizes.length + 1];
		for (int i = 0; i < sizes.length; i++) {
			ret[i] = read(sizes[i]);
		}
		if (requestData.position() < requestData.size())
			ret[ret.length - 1] = read((int) (requestData.size() - requestData
					.position()));
		else
			ret[ret.length - 1] = ByteBuffer.allocate(0);
		return ret;
	}

}
