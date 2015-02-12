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

/**
 * Used to send a message body.
 * 
 * An implementation might realize "chunking", for example.
 */
public abstract class AbstractBodyTransferrer {

	public abstract void write(byte[] content, int i, int length) throws IOException;
	public abstract void write(Chunk chunk) throws IOException;
	
	public abstract void finish() throws IOException;

}
