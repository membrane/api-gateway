/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples.env;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JavaLicenseInfoTest {

	private List<File> files = new ArrayList<File>();

	@Before
	public void precondition() {
		Assert.assertTrue(new File("../pom.xml").exists());
		Assert.assertTrue(new File("pom.xml").exists());
	}

	@Test
	public void doit() throws IOException {
		recurse(new File("..").getCanonicalFile());

		if (!files.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			sb.append("Missing license information in ");
			sb.append("" + files.size());
			sb.append(" files:\n");
			for (File file : files) {
				sb.append("  at ");
				sb.append(file.getAbsolutePath());
				sb.append(" (");
				sb.append(file.getName());
				sb.append(":1)\n");
			}
			String s = sb.toString();
			System.err.println(s);
			Assert.fail(s);
		}
	}

	private void recurse(File file) throws IOException {
		if (file.isFile()) {
			if (file.getName().endsWith(".java"))
				handle(file);
		}
		if (file.isDirectory()) {
			if (file.getName().equals("target"))
				return; // do not enter maven build directories
			for (File child : file.listFiles()) {
				recurse(child);
			}
		}
	}

	private void handle(File file) throws IOException {
		String content = FileUtils.readFileToString(file);


		//if (content.contains("Copyright"))
		//return;

		if (content.contains("Apache License"))
			return;

		if (content.contains("Copyright (c) 2013, Oracle and/or its affiliates"))
			return;

		files.add(file);
	}

}
