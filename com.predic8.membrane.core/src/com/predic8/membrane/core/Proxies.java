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

	// Control names
	public static final String ADJ_CONT_LENGTH = "auto_adjust_content_length";
	public static final String INDENT_MSG = "indent_message";
	public static final String INDENT_CONFIG = "indent_config";
	public static final String ADJ_HOST_HEADER = "adjust_host_header_field";

	public static final String PROXY_HOST = "proxy_host";
	public static final String PROXY_PORT = "proxy_port";
	public static final String USE_PROXY = "proxy_use";

	public static final String TRACK_EXCHANGE = "autotrack_new_exchanges";

	public static final String USE_PROXY_AUTH = "use proxy authentification";

	public static final String PROXY_AUTH_PASSWORD = "proxy authentification password";

	public static final String PROXY_AUTH_USERNAME = "proxy authentification username";

	private Collection<Rule> rules = new ArrayList<Rule>();

	public Map<String, Object> props = new HashMap<String, Object>();

	private ProxyConfiguration proxy;

	private Global global;

	public Proxies() {
		this(null);
	}

	public Proxies(Router router) {
		super(router);
		setAdjustHostHeader(true);
		setIndentMessage(true);
		setAdjustContentLength(true);
		setTrackExchange(false);
		global = new Global(router);
	}

	public void setRouter(Router router) {
		global.setRouter(router);
		super.setRouter(router);
	}

	public Map<String, Object> getProps() {
		return props;
	}

	public void setProps(Map<String, Object> props) {
		this.props = props;
	}

	public Collection<Rule> getRules() {
		return rules;
	}

	public void setRules(Collection<Rule> rules) {
		this.rules = rules;
	}

	public void setIndentMessage(boolean status) {
		props.put(INDENT_MSG, status);
	}

	public boolean getIndentMessage() {
		if (props.containsKey(INDENT_MSG)) {
			return (Boolean) props.get(INDENT_MSG);
		}
		return false;
	}

	public void setIndentConfig(boolean status) {
		props.put(INDENT_CONFIG, status);
	}

	public boolean getIndentConfig() {
		if (props.containsKey(INDENT_CONFIG)) {
			return (Boolean) props.get(INDENT_CONFIG);
		}
		return false;
	}

	public void setAdjustHostHeader(boolean status) {
		props.put(ADJ_HOST_HEADER, status);
	}

	public void setAdjustContentLength(boolean status) {
		props.put(ADJ_CONT_LENGTH, status);
	}

	public boolean getAdjustHostHeader() {
		if (props.containsKey(ADJ_HOST_HEADER)) {
			return (Boolean) props.get(ADJ_HOST_HEADER);
		}
		return true;
	}

	public boolean getAdjustContentLength() {
		if (props.containsKey(ADJ_CONT_LENGTH)) {
			return (Boolean) props.get(ADJ_CONT_LENGTH);
		}
		return false;
	}

	public void setTrackExchange(boolean status) {
		props.put(TRACK_EXCHANGE, status);
	}

	public boolean getTrackExchange() {
		if (props.containsKey(TRACK_EXCHANGE)) {
			return (Boolean) props.get(TRACK_EXCHANGE);
		}
		return false;
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

	public void setProxy(ProxyConfiguration proxy) {
		this.proxy = proxy;
	}

	public ProxyConfiguration getProxy() {
		return proxy;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if ("serviceProxy".equals(child)) {
			rules.add((ServiceProxy) new ServiceProxy(router).parse(token));
		} else if ("proxy".equals(child)) {
			rules.add((ProxyRule) new ProxyRule(router).parse(token));
		} else if ("global".equals(child)) {
			global.parse(token);
			props.putAll(global.getValues());
			proxy = global.getProxyConfiguration();
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartDocument(Constants.UTF_8, Constants.XML_VERSION);
		out.writeStartElement("proxies");

		for (Rule rule : rules) {
			rule.write(out);
		}

		global.setValues(props);
		global.setProxyConfiguration(proxy);
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
