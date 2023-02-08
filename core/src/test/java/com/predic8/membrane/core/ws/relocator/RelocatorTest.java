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

import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;
import static org.apache.commons.io.output.NullOutputStream.*;
import static org.junit.jupiter.api.Assertions.*;

public class RelocatorTest {

	private static Relocator relocator;

	@BeforeEach
	public void setUp() throws Exception {
		relocator = new Relocator(new OutputStreamWriter(
				NULL_OUTPUT_STREAM, UTF_8), "http", "localhost",
				3000, "", null);
	}

	@Test
	public void testWSDLRelocate() throws Exception {
		relocator.relocate(getFile("/blz-service.wsdl"));
		System.out.println("relocator.isWsdlFound() = " + relocator.isWsdlFound());
	}

	@NotNull
	private InputStreamReader getFile(String filename) throws IOException {
		return new InputStreamReader(new ByteArrayInputStream(
				getFileAsBytes(filename)), UTF_8);
	}


	@Test
	public void testXMLRelocate() throws Exception {
		relocator.relocate(getFile("/acl/acl.xml"));
		assertFalse(relocator.isWsdlFound());
	}

	@NotNull
	private byte[] getFileAsBytes(String name) throws IOException {
		return ByteUtil.getByteArrayData(requireNonNull(this.getClass()
				.getResourceAsStream(name)));
	}
}
