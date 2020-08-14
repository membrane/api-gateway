/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.FileInputStream;

import javax.xml.stream.XMLInputFactory;

import org.junit.Assert;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.SOAPUtil;


public class SOAPUtilTest {
	private XMLInputFactory xmlInputFactory;

	@Before
	public void setUp() throws Exception {

		xmlInputFactory = XMLInputFactory.newInstance();
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

	}

	@Test
	public void testFaultCheckSpecExample() throws Exception {
		Assert.assertTrue(SOAPUtil.isFault(xmlInputFactory, new XOPReconstitutor(), getMessage("src/test/resources/wsdlValidator/soapFaultFromSpec.xml")));
	}

	@Test
	public void testFaultCustom() throws Exception {

		Assert.assertTrue(SOAPUtil.isFault(xmlInputFactory, new XOPReconstitutor(), getMessage("src/test/resources/wsdlValidator/soapFaultCustom.xml")));
	}

	private Message getMessage(String path) throws Exception {
		return Response.ok().contentType("text/xml").body(new FileInputStream(path), true).build();
	}

}
