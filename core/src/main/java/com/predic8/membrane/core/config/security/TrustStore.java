/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config.security;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name="truststore", group="ssl")
public class TrustStore extends Store {

	public static final String ELEMENT_NAME = "truststore";

	protected String algorithm;

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		super.parseAttributes(token);
		algorithm = token.getAttributeValue("", "algorithm");
	}
	
	protected void writeAttributes(XMLStreamWriter out) throws XMLStreamException {
		super.writeAttributes(out);
		if (algorithm != null)
			out.writeAttribute("algorithm", algorithm);
	}
	
	public String getAlgorithm() {
		return algorithm;
	}
	
	@MCAttribute
	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}
	
}
