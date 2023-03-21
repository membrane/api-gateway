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

import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.*;
import static org.apache.commons.io.FileUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class JavaLicenseInfoTest {

	private final List<File> files = new ArrayList<>();

	@BeforeEach
	public void precondition() {
		assertTrue(new File("../pom.xml").exists());
		assertTrue(new File("pom.xml").exists());
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
			fail(s);
		}
	}

	@SuppressWarnings("DataFlowIssue")
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
		String content = readFileToString(file, UTF_8);

		if (content.contains("Apache License"))
			return;

		if (content.contains("Copyright (c) 2013, Oracle and/or its affiliates"))
			return;

		files.add(file);
	}

}
