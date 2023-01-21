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

package com.predic8.membrane.core.util;

import java.io.*;
import java.util.stream.*;

import static java.util.stream.Collectors.joining;

public class FileUtil {

	public static String readInputStream(InputStream is) {
		return new BufferedReader(new InputStreamReader(is)).lines().collect(joining("\n"));
	}

	public static void writeInputStreamToFile(String filepath, InputStream is) throws IOException {
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(filepath))) {
			byte[] buffer = new byte[1024];
			int len;
			while ((len = is.read(buffer)) > 0) {
				os.write(buffer, 0, len);
				os.flush();
			}
		}
	}
}
