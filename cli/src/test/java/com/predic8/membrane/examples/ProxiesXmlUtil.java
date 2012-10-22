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

package com.predic8.membrane.examples;

import java.io.File;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.parboiled.common.FileUtils;

import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;

public class ProxiesXmlUtil {
	
	private File proxiesXml;

	public ProxiesXmlUtil(String file) {
		proxiesXml = new File(file);
		if (!proxiesXml.exists())
			throw new IllegalArgumentException("File " + file + " does not exist.");
	}
	
	public ProxiesXmlUtil(File file) {
		proxiesXml = file;
		if (!proxiesXml.exists())
			throw new IllegalArgumentException("File " + file + " does not exist.");
	}
	
	public void updateWith(String proxiesXmlContent, Process2 sl) {
		SubstringWaitableConsoleEvent reloaded = new SubstringWaitableConsoleEvent(sl, "listening at port " + getLastPort(proxiesXmlContent));
		FileUtils.writeAllText(proxiesXmlContent, proxiesXml);
		reloaded.waitFor(10000);
	}
	
	private static int getLastPort(String proxiesXmlContent) {
		Pattern p = Pattern.compile("port=\"(\\d+)\"");
		Matcher m = p.matcher(proxiesXmlContent);
		String port = null;
		while (m.find())
			port = m.group(1);
		if (port == null)
			throw new RuntimeException("Could not find port in proxies.xml '" + proxiesXmlContent + "'.");
		return Integer.parseInt(port);
	}

}
