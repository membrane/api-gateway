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

package com.predic8.membrane.servlet.embedded;

import java.io.IOException;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCMain;
import com.predic8.membrane.core.transport.SSLContext;
import com.predic8.membrane.core.transport.Transport;

@MCMain(
		outputPackage="com.predic8.membrane.servlet.config.spring",
		outputName="router-conf.xsd",
		xsd="" +
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
				"<xsd:schema xmlns=\"http://membrane-soa.org/war/1/\"\r\n" + 
				"	xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:beans=\"http://www.springframework.org/schema/beans\"\r\n" + 
				"	targetNamespace=\"http://membrane-soa.org/war/1/\"\r\n" + 
				"	elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">\r\n" + 
				"\r\n" + 
				"	<xsd:import namespace=\"http://www.springframework.org/schema/beans\" schemaLocation=\"http://www.springframework.org/schema/beans/spring-beans-3.1.xsd\" />\r\n" + 
				"\r\n" + 
				"${declarations}\r\n" +
				"\r\n" +
				"${raw}\r\n" +
				"	\r\n" + 
				"	<xsd:complexType name=\"EmptyElementType\">\r\n" + 
				"		<xsd:complexContent>\r\n" + 
				"			<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"				<xsd:sequence />\r\n" + 
				"			</xsd:extension>\r\n" + 
				"		</xsd:complexContent>\r\n" + 
				"	</xsd:complexType>\r\n" + 
				"	\r\n" + 
				"</xsd:schema>")
@MCElement(name="servletTransport", group="transport", configPackage="com.predic8.membrane.servlet.config.spring")
public class ServletTransport extends Transport {

	boolean removeContextRoot = true;
	
	public boolean isRemoveContextRoot() {
		return removeContextRoot;
	}

	@MCAttribute
	public void setRemoveContextRoot(boolean removeContextRoot) {
		this.removeContextRoot = removeContextRoot;
	}
	
	@Override
	public void openPort(String ip, int port, SSLContext sslContext) throws IOException {
		// do nothing
	}
	
	@Override
	public void closeAll() throws IOException {
		// do nothing
	}

	@Override
	public boolean isOpeningPorts() {
		return false;
	}
	
}
