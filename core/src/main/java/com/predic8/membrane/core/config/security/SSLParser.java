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

import com.predic8.membrane.core.config.AbstractConfigElement;

public class SSLParser extends AbstractConfigElement {

	private KeyStore keyStore;
	private TrustStore trustStore;
	private String algorithm;
	private String protocol;
	private String ciphers;
	private String clientAuth;
	private boolean ignoreTimestampCheckFailure;

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		algorithm = token.getAttributeValue("", "algorithm");
		protocol = token.getAttributeValue("", "protocol");
		ciphers = token.getAttributeValue("", "ciphers");
		clientAuth = token.getAttributeValue("", "clientAuth");
		ignoreTimestampCheckFailure = Boolean.parseBoolean(token.getAttributeValue("", "ignoreTimestampCheckFailure"));
		super.parseAttributes(token);
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (KeyStore.ELEMENT_NAME.equals(child)) {
			keyStore = new KeyStore();
			keyStore.parse(token);
		} else if (TrustStore.ELEMENT_NAME.equals(child)) {
			trustStore = new TrustStore();
			trustStore.parse(token);
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("ssl");
		if (algorithm != null)
			out.writeAttribute("algorithm", algorithm);
		if (protocol != null)
			out.writeAttribute("protocol", protocol);
		if (ciphers != null)
			out.writeAttribute("ciphers", ciphers);
		if (clientAuth != null)
			out.writeAttribute("clientAuth", clientAuth);
		if (ignoreTimestampCheckFailure)
			out.writeAttribute("ignoreTimestampCheckFailure", "true");
		

		if (keyStore != null)
			keyStore.write(out);
		if (trustStore != null)
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
	
	public String getAlgorithm() {
		return algorithm;
	}
	
	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}
	
	public String getProtocol() {
		return protocol;
	}
	
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}
	
	public String getCiphers() {
		return ciphers;
	}
	
	public void setCiphers(String ciphers) {
		this.ciphers = ciphers;
	}
	
	public String getClientAuth() {
		return clientAuth;
	}
	
	public void setClientAuth(String clientAuth) {
		this.clientAuth = clientAuth;
	}

	public boolean isIgnoreTimestampCheckFailure() {
		return ignoreTimestampCheckFailure;
	}
	
	public void setIgnoreTimestampCheckFailure(boolean ignoreTimestampCheckFailure) {
		this.ignoreTimestampCheckFailure = ignoreTimestampCheckFailure;
	}

}
