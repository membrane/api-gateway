/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http;

import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * A {@link BufferedInputStream} that tells how many bytes it holds in its buffer.
 * {@link BufferedInputStream#available()} cannot tell that: it adds the estimate of the underlying
 * stream, which may be too high (e.g. {@link java.util.zip.GZIPInputStream} reports 1 until it
 * reaches the end).
 */
public final class BufferedConnectionInputStream extends BufferedInputStream {

	public BufferedConnectionInputStream(InputStream in, int size) {
		super(in, size);
	}

	/**
	 * @return the number of bytes that can be read without reading from the underlying stream
	 */
	public synchronized int buffered() {
		return count - pos;
	}
}
