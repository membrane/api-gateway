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

package com.predic8.membrane.examples.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.*;
import java.util.concurrent.TimeoutException;

import com.predic8.membrane.examples.util.*;
import org.apache.commons.io.FileUtils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.writeStringToFile;

public class ProxiesXmlUtil {

	private final File proxiesXml;

	public ProxiesXmlUtil(File file) {
		proxiesXml = file;
		if (!proxiesXml.exists())
			throw new IllegalArgumentException("File " + file + " does not exist.");
	}

	public void updateWith(String proxiesXmlContent, Process2 sl) throws IOException, TimeoutException {
		SubstringWaitableConsoleEvent reloaded = new SubstringWaitableConsoleEvent(sl, "Spring Hot Deployment Thread started.");
		writeStringToFile(proxiesXml, proxiesXmlContent, UTF_8);
		reloaded.waitFor(60000);
	}
}
