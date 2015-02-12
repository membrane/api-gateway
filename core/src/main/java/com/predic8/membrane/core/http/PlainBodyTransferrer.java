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

public class PlainBodyTransferrer extends AbstractBodyTransferrer {
	OutputStream out;
	
	public PlainBodyTransferrer(OutputStream out) {
		this.out = out;
	}
	
	public void write(byte[] content, int i, int length) throws IOException {
		out.write(content, i, length);
	}

	public void write(Chunk chunk) throws IOException {
		out.write(chunk.getContent());
	}

	public void finish() throws IOException {
	}
}
