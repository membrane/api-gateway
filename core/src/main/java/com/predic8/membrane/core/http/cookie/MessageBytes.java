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
package com.predic8.membrane.core.http.cookie;

import com.predic8.membrane.core.http.*;

import java.util.*;

import static java.nio.charset.StandardCharsets.*;

/**
 * Adapter between Tomcat classes ({@link ServerCookie} etc.) and Membrane
 * classes ({@link Request} etc.).
 */
public final class MessageBytes  {
	private static final byte[] empty = new byte[0];

	private byte[] bytes;
	private int offset;
	private int length;

	public static MessageBytes newInstance() {
		return new MessageBytes();
	}

	public void recycle() {
		setEmpty();
	}

	public ByteChunk getByteChunk() {
		ByteChunk b = new ByteChunk();
		b.setBytes(bytes, offset, length);
		return b;
	}

	// @Todo Why is this always false?
	public boolean isNull() {
		return false;
	}

	public void setBytes(byte[] bytes, int offset, int length) {
		this.bytes = bytes;
		this.offset = offset;
		this.length = length;
	}

	public void setEmpty() {
		bytes = empty;
		offset = 0;
		length = 0;
	}

	@Override
	public String toString() {
		return new String(bytes, offset, length, ISO_8859_1);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof String str)
			return toString().equals(str);
		if (obj instanceof MessageBytes mb)
			return offset == mb.offset && length == mb.length && Arrays.equals(bytes,mb.bytes);
		return false;
	}
}
