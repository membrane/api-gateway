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

package com.predic8.membrane.core.util;

import java.io.IOException;
import java.io.InputStream;


public class ByteUtil {
	
//	private static Log log = LogFactory.getLog(ByteUtil.class.getName());
	
	public static byte[] readByteArray(InputStream in, int length) throws IOException {
		byte[] content = new byte[length];
		int offset = 0;
		int count = 0;
		while (offset < length && (count = in.read(content, offset, length - offset)) >= 0) {
			offset += count;
		}
		return content;
	}

}
