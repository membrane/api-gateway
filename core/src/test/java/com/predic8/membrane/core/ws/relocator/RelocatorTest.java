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

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import junit.framework.TestCase;

import org.apache.commons.io.output.NullOutputStream;
import org.junit.Test;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.ByteUtil;

public class RelocatorTest extends TestCase {

	private Relocator relocator;

	@Override
	protected void setUp() throws Exception {
		relocator = new Relocator(new OutputStreamWriter(
				NullOutputStream.NULL_OUTPUT_STREAM, Constants.UTF_8), "http", "localhost",
				3000, null, null);
		super.setUp();
	}

	public void testWSDLRelocate() throws Exception {
		byte[] contentWSDL = ByteUtil.getByteArrayData(this.getClass()
				.getResourceAsStream("/blz-service.wsdl"));
		relocator.relocate(new InputStreamReader(new ByteArrayInputStream(
				contentWSDL), Constants.UTF_8));
		assertTrue(relocator.isWsdlFound());
	}

	@Test
	public void testXMLRelocate() throws Exception {
		byte[] contentXML = ByteUtil.getByteArrayData(this.getClass()
				.getResourceAsStream("/acl/acl.xml"));
		relocator.relocate(new InputStreamReader(new ByteArrayInputStream(
				contentXML), Constants.UTF_8));
		assertFalse(relocator.isWsdlFound());
	}
}
