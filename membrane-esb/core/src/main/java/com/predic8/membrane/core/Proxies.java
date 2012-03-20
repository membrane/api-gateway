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

package com.predic8.membrane.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractConfigElement;
import com.predic8.membrane.core.config.Global;
import com.predic8.membrane.core.config.ProxyConfiguration;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.util.TextUtil;

public class Proxies extends AbstractConfigElement {
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
		FileOutputStream fos = new FileOutputStream(path);
		try {
			OutputStreamWriter out = new OutputStreamWriter(fos, Constants.UTF_8_CHARSET);
			out.write(TextUtil.formatXML(new InputStreamReader(
				new ByteArrayInputStream(toBytes()), Constants.UTF_8)));
			out.flush();
			out.close();
		} finally {
			fos.close();
		}

	}

}
