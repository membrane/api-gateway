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

package com.predic8.membrane.core;

import java.io.*;
import java.util.*;

import javax.xml.stream.*;

import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.TextUtil;

public class Proxies extends AbstractConfigElement {

	private static final long serialVersionUID = 1L;

	private Collection<Rule> rules = new ArrayList<Rule>();

	private Global global;

	public Proxies() {
		this(null);
	}

	public Proxies(Router router) {
		super(router);
		global = new Global(router);
	}

	public void setRouter(Router router) {
		global.setRouter(router);
		super.setRouter(router);
	}

	public Collection<Rule> getRules() {
		return rules;
	}

	public void setRules(Collection<Rule> rules) {
		this.rules = rules;
	}

	public void setIndentMessage(boolean status) {
		global.setIndentMessage(status);
	}

	public boolean getIndentMessage() {
		return global.getIndentMessage();
	}

	public void setAdjustContentLength(boolean status) {
		global.setAdjustContentLength(status);
	}

	public void setAdjustHostHeader(boolean status) {
		global.setAdjustHostHeader(status);
	}
	
	public boolean getAdjustHostHeader() {
		return global.getAdjustHostHeader();
	}

	public boolean getAdjustContentLength() {
		return global.getAdjustContentLength();
	}

	public void setTrackExchange(boolean status) {
		global.setTrackExchange(status);
	}

	public boolean getTrackExchange() {
		return global.getTrackExchange();
	}

	public String getKeyStoreLocation() {
		return global.getSecurity().getKeyStore().getLocation();
	}

	public String getTrustStoreLocation() {
		return global.getSecurity().getTrustStore().getLocation();
	}

	public void setKeyStoreLocation(String location) {
		global.getSecurity().getKeyStore().setLocation(location);
	}

	public void setTrustStoreLocation(String location) {
		global.getSecurity().getTrustStore().setLocation(location);
	}

	public String getKeyStorePassword() {
		return global.getSecurity().getKeyStore().getPassword();
	}

	public String getTrustStorePassword() {
		return global.getSecurity().getTrustStore().getPassword();
	}

	public void setKeyStorePassword(String password) {
		global.getSecurity().getKeyStore().setPassword(password);
	}

	public void setTrustStorePassword(String password) {
		global.getSecurity().getTrustStore().setPassword(password);
	}

	public void setProxyConfiguration(ProxyConfiguration proxy) {
		global.setProxyConfiguration(proxy);
	}

	public ProxyConfiguration getProxyConfiguration() {
		return global.getProxyConfiguration();
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if ("serviceProxy".equals(child)) {
			rules.add((ServiceProxy) new ServiceProxy(router).parse(token));
		} else if ("proxy".equals(child)) {
			rules.add((ProxyRule) new ProxyRule(router).parse(token));
		} else if ("global".equals(child)) {
			global.parse(token);
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartDocument(Constants.UTF_8, Constants.XML_VERSION);
		out.writeStartElement("proxies");

		for (Rule rule : rules) {
			rule.write(out);
		}

		global.write(out);

		out.writeEndElement();
		out.writeEndDocument();
	}

	public boolean isKeyStoreAvailable() {
		return getKeyStoreLocation() != null
				&& !"".equals(getKeyStoreLocation().trim())
				&& getKeyStorePassword() != null;
	}

	public byte[] toBytes() throws Exception, FactoryConfigurationError {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		XMLStreamWriter writer = XMLOutputFactory.newInstance()
				.createXMLStreamWriter(baos, Constants.UTF_8);
		write(writer);
		writer.flush();
		writer.close();

		return baos.toByteArray();
	}

	public void write(String path) throws Exception {
		FileWriter out = new FileWriter(path);
		out.write(TextUtil.formatXML(new InputStreamReader(
				new ByteArrayInputStream(toBytes()), Constants.UTF_8)));
		out.flush();
		out.close();

	}

}
