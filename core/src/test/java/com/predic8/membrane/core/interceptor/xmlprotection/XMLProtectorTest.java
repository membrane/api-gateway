/* Copyright 2010, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xmlprotection;

import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.io.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class XMLProtectorTest {

    private static final Logger LOG = LoggerFactory.getLogger(XMLProtectorTest.class);
	private XMLProtector xmlProtector;
	private byte[] input, output;

	private boolean runOn(String resource) throws Exception {
		return runOn(resource, true);
	}

	private boolean runOn(String resource, boolean removeDTD) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		xmlProtector = new XMLProtector(new OutputStreamWriter(baos, UTF_8), removeDTD, 1000, 1000);
		input = ByteUtil.getByteArrayData(this.getClass().getResourceAsStream(resource));

		if (resource.endsWith("lmx")) {
			reverse();
		}

		boolean valid = xmlProtector.protect(new InputStreamReader(new ByteArrayInputStream(input), UTF_8));
		if (!valid) {
			output = null;
		} else {
			output = baos.toByteArray();
		}
		return valid;
	}

	private void reverse() {
		// To reverse the input Stream
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			bos.write(input);
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
		StringBuilder sb = new StringBuilder();
		sb.append(bos);
		sb.reverse();
		input = String.valueOf(sb).getBytes();
	}

	@Test
	public void testInvariant() throws Exception {
		assertTrue(runOn("/customer.xml"));
	}

	@Test
	public void testNotWellformed() throws Exception {
		assertFalse(runOn("/xml/not-wellformed.xml"));
	}

	@Test
	public void testDTDRemoval1() throws Exception {
		assertTrue(runOn("/xml/entity-expansion.lmx"));
		assertTrue(output.length < input.length / 2);
		assertFalse(new String(output).contains("ENTITY"));
	}

	@Test
	public void testDTDRemoval2() throws Exception {
		assertTrue(runOn("/xml/entity-external.xml"));
		assertTrue(output.length < input.length * 2 / 3);
		assertFalse(new String(output).contains("ENTITY"));
	}

	@Test
	public void testExpandingEntities() throws Exception {
		assertTrue(runOn("/xml/entity-expansion.lmx", false));
		assertTrue(output.length > input.length / 2);
		assertTrue(new String(output).contains("ENTITY"));
	}

	@Test
	public void testExternalEntities() throws Exception {
		assertTrue(runOn("/xml/entity-external.xml", false));
		assertTrue(output.length > input.length * 2 / 3);
		assertTrue(new String(output).contains("ENTITY"));
	}

	@Test
	public void testLongElementName() throws Exception {
		assertFalse(runOn("/xml/long-element-name.xml"));
	}

	@Test
	public void testManyAttributes() throws Exception {
		assertFalse(runOn("/xml/many-attributes.xml"));
	}
}
