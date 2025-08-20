/* Copyright 2010, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.ws.relocator;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class RelocatorTest {

	private static Relocator relocator;
	ByteArrayOutputStream os;

	@BeforeEach
	public void setUp() throws Exception {

		os = new ByteArrayOutputStream();

		relocator = new Relocator(new OutputStreamWriter(
				os, UTF_8), "http", "localhost",
				3000, "", null);
	}

	@Test
	void wsdl() throws Exception {
		relocator.relocate(getFile("/blz-service.wsdl"));
		assertTrue(relocator.isWsdlFound());
		System.out.println("os.toString(UTF_8) = " + os.toString(UTF_8));
	}

	@Test
	void xml() throws Exception {
		relocator.relocate(getFile("/acl/acl.xml"));
		assertFalse(relocator.isWsdlFound());
	}

	@NotNull
	private InputStream getFile(String filename) {
		return this.getClass().getResourceAsStream(filename);
	}
}
