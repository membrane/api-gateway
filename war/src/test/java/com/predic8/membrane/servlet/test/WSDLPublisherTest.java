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
package com.predic8.membrane.servlet.test;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.apache.http.ParseException;
import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.test.WSDLUtil;

public class WSDLPublisherTest {

	@Test
	public void doit() throws ParseException, IOException, XMLStreamException {
		Assert.assertEquals(5, WSDLUtil.countWSDLandXSDs("http://localhost:3021/wsdlPublisher/?wsdl"));
	}

}
