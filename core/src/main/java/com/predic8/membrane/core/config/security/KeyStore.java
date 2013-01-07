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



public class KeyStore extends Store {

	public static final String ELEMENT_NAME = "keystore";

	private String keyPassword;
	private String keyAlias;

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		super.parseAttributes(token);
		keyPassword = token.getAttributeValue("", "keyPassword");
		keyAlias = token.getAttributeValue("", "keyAlias");
	}
	
	@Override
	protected void writeAttributes(XMLStreamWriter out)
			throws XMLStreamException {
		super.writeAttributes(out);
		if (keyPassword != null)
			out.writeAttribute("keyPassword", keyPassword);
		if (keyAlias != null)
			out.writeAttribute("keyAlias", keyAlias);
	}

	public String getKeyPassword() {
		return keyPassword;
	}
	
	@MCAttribute
	public void setKeyPassword(String keyPassword) {
		this.keyPassword = keyPassword;
	}
	
	public String getKeyAlias() {
		return keyAlias;
	}
	
	@MCAttribute
	public void setKeyAlias(String keyAlias) {
		this.keyAlias = keyAlias;
	}
	
}
