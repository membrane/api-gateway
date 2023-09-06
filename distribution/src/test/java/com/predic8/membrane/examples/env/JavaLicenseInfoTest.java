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
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.io.FileUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class JavaLicenseInfoTest {

	@BeforeEach
	public void precondition() {
		assertTrue(new File("../pom.xml").exists());
		assertTrue(new File("pom.xml").exists());
	}

	@Test
	public void checkFiles() throws IOException {
		var files = findJavaFiles(new File("..").getCanonicalFile()).stream()
				.filter(file -> !containsLicense(file))
				.toList();
		if (!files.isEmpty()) {
			String s = getFailureMessage(files);
			System.out.println(s);
			fail(s);
		}
	}

	private List<File> findJavaFiles(File startLocation) {
		if (startLocation.isFile()) {
			if (startLocation.getName().endsWith(".java"))
				return List.of(startLocation);
		} else if (startLocation.isDirectory()) {
			if (startLocation.getName().equals("target"))
				return emptyList(); // do not enter maven build directories
			return Arrays.stream(Objects.requireNonNull(startLocation.listFiles()))
					.flatMap(file -> findJavaFiles(file).stream())
					.toList();
		}
		return emptyList();
	}

	private static boolean containsLicense(File file) {
		try {
			String content = readFileToString(file, UTF_8);
			return content.contains("Apache License") || content.contains("Copyright (c) 2013, Oracle and/or its affiliates");
		} catch (Exception e) {
			return false;
		}
	}

	private String getFailureMessage(List<File> files) {
		return "Missing license information in %d files:%n".formatted(files.size()) +
				files.stream().map(JavaLicenseInfoTest::formatFileAsStacktraceLine).collect(joining("\n"));
	}

	private static String formatFileAsStacktraceLine(File file) {
		return "  at %s (%s:1))".formatted(file.getAbsolutePath(), file.getName());
	}
}
