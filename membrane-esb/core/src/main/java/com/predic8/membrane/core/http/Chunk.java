/* Copyright 2009 predic8 GmbH, www.predic8.com

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

public class Chunk {

	private byte[] content;
	
	public Chunk(byte[] content) {
		this.content = content;
	}

	public Chunk(String stringContent) {
		content = stringContent.getBytes();
	}
	
	public byte[] getContent() {
		return content;
	}
	
	public void setContent(byte[] content) {
		this.content = content;
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
		out.write(Constants.CRLF.getBytes());
		out.write(getContent(), 0, getLength());
		out.write(Constants.CRLF.getBytes());
		out.flush();
	}

	@Override
	public String toString() {
		if (content == null)
			return "";
		return new String(content);
	}

	public int copyChunk(byte[] raw, int destPos) {
		System.arraycopy(content, 0, raw, destPos, getLength());
		return destPos += getLength();
	}

	public int copyChunkLength(byte[] raw, int destPos, AbstractBody body) {
		System.arraycopy(getLengthBytes(), 0, raw, destPos, getLengthBytes().length);
		return destPos += getLengthBytes().length;
	}

	private byte[] getLengthBytes() {
		return Long.toHexString(getLength()).getBytes();
	}
	
}
