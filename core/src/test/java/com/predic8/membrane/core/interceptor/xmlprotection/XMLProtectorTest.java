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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import junit.framework.TestCase;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtector;
import com.predic8.membrane.core.util.ByteUtil;

public class XMLProtectorTest extends TestCase {

	private XMLProtector xmlProtector;
	private byte[] input, output;
	
	private boolean runOn(String resource) throws Exception {
		return runOn(resource, true);
	}
	
	private boolean runOn(String resource, boolean removeDTD) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		xmlProtector = new XMLProtector(new OutputStreamWriter(baos, Constants.UTF_8), removeDTD, 1000, 1000);
		input = ByteUtil.getByteArrayData(this.getClass().getResourceAsStream(resource));
		boolean valid = xmlProtector.protect(new InputStreamReader(new ByteArrayInputStream(input), Constants.UTF_8));
		if (!valid) {
			output = null;
		} else {
			output = baos.toByteArray();
		}
		return valid;
	}
	
	public void testInvariant() throws Exception {
		assertTrue(runOn("/customer.xml"));
	}

	public void testNotWellformed() throws Exception {
		assertFalse(runOn("/xml/not-wellformed.xml"));
	}

	public void testDTDRemoval1() throws Exception {
		assertTrue(runOn("/xml/entity-expansion.xml"));
		assertTrue(output.length < input.length / 2);
		assertFalse(new String(output).contains("ENTITY"));
	}

	public void testDTDRemoval2() throws Exception {
		assertTrue(runOn("/xml/entity-external.xml"));
		assertTrue(output.length < input.length * 2 / 3);
		assertFalse(new String(output).contains("ENTITY"));
	}

	public void testExpandingEntities() throws Exception {
		assertTrue(runOn("/xml/entity-expansion.xml", false));
		assertTrue(output.length > input.length / 2);
		assertTrue(new String(output).contains("ENTITY"));
	}

	public void testExternalEntities() throws Exception {
		assertTrue(runOn("/xml/entity-external.xml", false));
		assertTrue(output.length > input.length * 2 / 3);
		assertTrue(new String(output).contains("ENTITY"));
	}

	public void testLongElementName() throws Exception {
		assertFalse(runOn("/xml/long-element-name.xml"));
	}

	public void testManyAttributes() throws Exception {
		assertFalse(runOn("/xml/many-attributes.xml"));
	}

}
