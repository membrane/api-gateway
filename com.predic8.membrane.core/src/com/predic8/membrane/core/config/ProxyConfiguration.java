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

package com.predic8.membrane.core.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.codec.binary.Base64;

import com.predic8.membrane.core.Router;

public class ProxyConfiguration extends AbstractConfigElement {

	public static final String ELEMENT_NAME = "proxyConfiguration";

	public static final String ATTRIBUTE_ACTIVE = "active";
	
	public static final String ATTRIBUTE_AUTHENTICATION = "authentication";
	
	private boolean useProxy;
	
	private String proxyHost;

	private int proxyPort;


	private boolean useAuthentication;
	
	private String proxyUsername;
	
	private String proxyPassword;

	public ProxyConfiguration(Router router) {
		super(router);
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		useProxy = getBoolean(token, ATTRIBUTE_ACTIVE);
		useAuthentication = getBoolean(token, ATTRIBUTE_AUTHENTICATION);
		super.parseAttributes(token);
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (ProxyHost.ELEMENT_NAME.equals(child)) {
			proxyHost = ((ProxyHost) (new ProxyHost().parse(token))).value;
		} else if (ProxyPort.ELEMENT_NAME.equals(child)) {
			proxyPort = Integer.parseInt(((ProxyPort) (new ProxyPort().parse(token))).getValue());
		} else if (ProxyUsername.ELEMENT_NAME.equals(child)) {
			proxyUsername = ((ProxyUsername) (new ProxyUsername().parse(token))).getValue();
		} else if (ProxyPassword.ELEMENT_NAME.equals(child)) {
			proxyPassword = ((ProxyPassword) (new ProxyPassword().parse(token))).getValue();
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);

		out.writeAttribute(ATTRIBUTE_ACTIVE, Boolean.toString(useProxy));

		out.writeAttribute(ATTRIBUTE_AUTHENTICATION, Boolean.toString(useAuthentication));

		new ProxyHost(proxyHost).write(out);

		new ProxyPort(Integer.toString(proxyPort)).write(out);

		new ProxyUsername(proxyUsername).write(out);

		new ProxyPassword(proxyPassword).write(out);

		out.writeEndElement();
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public String getProxyPassword() {
		return proxyPassword;
	}

	public void setProxyPassword(String proxyPassword) {
		this.proxyPassword = proxyPassword;
	}

	public boolean useProxy() {
		return useProxy;
	}

	public void setUseProxy(boolean useProxy) {
		this.useProxy = useProxy;
	}

	public String getProxyUsername() {
		return proxyUsername;
	}

	public void setProxyUsername(String proxyUsername) {
		this.proxyUsername = proxyUsername;
	}

	public boolean isUseAuthentication() {
		return useAuthentication;
	}

	public void setUseAuthentication(boolean useAuthentication) {
		this.useAuthentication = useAuthentication;
	}
	
	public String getCredentials() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Basic ");
		byte[] base64UserPass = Base64.encodeBase64((proxyUsername + ":" + proxyPassword).getBytes());
		buffer.append(new String(base64UserPass));
		return buffer.toString();
	}

}
