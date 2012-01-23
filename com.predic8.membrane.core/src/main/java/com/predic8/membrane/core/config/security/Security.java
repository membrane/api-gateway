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

import javax.xml.stream.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractConfigElement;

public class Security extends AbstractConfigElement {

	private KeyStore keyStore;

	private TrustStore trustStore;

	public Security(Router router) {
		super(router);
		keyStore = new KeyStore(router);
		trustStore = new TrustStore(router);
	}

	public boolean isSet() {
		return keyStore.location != null || trustStore.location != null;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (KeyStore.ELEMENT_NAME.equals(child)) {
			keyStore.parse(token);
		}

		if (TrustStore.ELEMENT_NAME.equals(child)) {
			trustStore.parse(token);
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("security");

		keyStore.write(out);
		trustStore.write(out);

		out.writeEndElement();
	}

	public KeyStore getKeyStore() {
		return keyStore;
	}

	public void setKeyStore(KeyStore keyStore) {
		this.keyStore = keyStore;
	}

	public TrustStore getTrustStore() {
		return trustStore;
	}

	public void setTrustStore(TrustStore trustStore) {
		this.trustStore = trustStore;
	}

}
