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

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;

public class ProxyConfiguration extends AbstractConfigElement {

	public static final String ATTRIBUTE_ACTIVE = "active";

	public static final String ATTRIBUTE_AUTHENTICATION = "authentication";

	private boolean useProxy;

	private String host;

	private int port;

	private boolean useAuthentication;

	private String username;

	private String password;

	public ProxyConfiguration(Router router) {
		super(router);
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		useProxy = getBoolean(token, ATTRIBUTE_ACTIVE);
		useAuthentication = getBoolean(token, ATTRIBUTE_AUTHENTICATION);
		host = token.getAttributeValue("", "host");
		port = Integer.parseInt(token.getAttributeValue("", "port"));
		username = token.getAttributeValue("", "username");
		password = token.getAttributeValue("", "password");
		super.parseAttributes(token);
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("proxyConfiguration");

		out.writeAttribute(ATTRIBUTE_ACTIVE, "" + useProxy);

		out.writeAttribute(ATTRIBUTE_AUTHENTICATION, "" + useAuthentication);

		out.writeAttribute("host", host);
		out.writeAttribute("port", "" + port);
		out.writeAttribute("password", password);
		out.writeAttribute("username", username);

		out.writeEndElement();
	}

	public String getProxyHost() {
		return host;
	}

	public void setProxyHost(String proxyHost) {
		this.host = proxyHost;
	}

	public int getProxyPort() {
		return port;
	}

	public void setProxyPort(int proxyPort) {
		this.port = proxyPort;
	}

	public String getProxyPassword() {
		return password;
	}

	public void setProxyPassword(String proxyPassword) {
		this.password = proxyPassword;
	}

	public boolean useProxy() {
		return useProxy;
	}

	public void setUseProxy(boolean useProxy) {
		this.useProxy = useProxy;
	}

	public String getProxyUsername() {
		return username;
	}

	public void setProxyUsername(String proxyUsername) {
		this.username = proxyUsername;
	}

	public boolean isUseAuthentication() {
		return useAuthentication;
	}

	public void setUseAuthentication(boolean useAuthentication) {
		this.useAuthentication = useAuthentication;
	}

	/**
	 * The "Basic" authentication scheme defined in RFC 2617 does not properly define how to treat non-ASCII characters.
	 */
	public String getCredentials() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Basic ");
		byte[] base64UserPass = Base64
				.encodeBase64((username + ":" + password).getBytes(Constants.UTF_8_CHARSET));
		buffer.append(new String(base64UserPass, Constants.UTF_8_CHARSET));
		return buffer.toString();
	}

}
