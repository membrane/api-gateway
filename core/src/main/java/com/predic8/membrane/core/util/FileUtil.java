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

public class FileUtil {
	public static File prefixMembraneHomeIfNeeded(File f) {
		if ( f.isAbsolute() )
			return f;

		return new File(System.getenv("MEMBRANE_HOME"), f.getPath());

	}

	public static String readInputStream(InputStream is) {
		InputStreamReader in = new InputStreamReader(is);
		Stream<String> lines = new BufferedReader(in).lines();
		return lines.collect(Collectors.joining("\n"));
	}


}
