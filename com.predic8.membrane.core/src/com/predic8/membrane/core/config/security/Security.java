/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.config.AbstractConfigElement;

public class Security extends AbstractConfigElement {

	public static final String ELEMENT_NAME = "security";

	private KeyStore keyStore;
	
	private TrustStore trustStore;
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	public String getKeyStoreLocation() {
		if (keyStore != null)
			return keyStore.getLocation();
		return null;
	}
	
	public String getTrustStoreLocation() {
		if (trustStore != null)
			return trustStore.getLocation();
		return null;
	}
	
	public String getKeyStorePassword() {
		if (keyStore != null)
			return keyStore.getPassword();
		return null;
	}
	
	public String getTrustStorePassword() {
		if (trustStore != null)
			return trustStore.getPassword();
		return null;
	}

	public void setValues(Map<String, Object> props) {
		keyStore = new KeyStore();
		setKeyStoreLocation(props.get(Configuration.KEY_STORE_LOCATION));
		setKeyStorePassword(props.get(Configuration.KEY_STORE_PASSWORD));
		
		
		trustStore = new TrustStore();
		setTrustStoreLocation(props.get(Configuration.TRUST_STORE_LOCATION));
		setTrustStorePassword(props.get(Configuration.TRUST_STORE_PASSWORD));
		
	}
	
	private void setKeyStoreLocation(Object location) {
		if (location == null)
			return;
		keyStore.setLocation(location.toString());
	}
	
	private void setKeyStorePassword(Object password) {
		if (password == null)
			return;
		keyStore.setPassword(password.toString());
	}
	
	private void setTrustStoreLocation(Object location) {
		if (location == null)
			return;
		trustStore.setLocation(location.toString());
	}
	
	private void setTrustStorePassword(Object password) {
		if (password == null)
			return;
		trustStore.setPassword(password.toString());
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (KeyStore.ELEMENT_NAME.equals(child)) {
			keyStore = ((KeyStore) new KeyStore().parse(token));
		} 
		
		if (TrustStore.ELEMENT_NAME.equals(child)) {
			trustStore = ((TrustStore) new TrustStore().parse(token));
		} 
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		
		keyStore.write(out);
		trustStore.write(out);
		
		out.writeEndElement();
	}
}
