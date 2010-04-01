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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXMLElement;
import com.predic8.membrane.core.config.Format;
import com.predic8.membrane.core.config.GUI;
import com.predic8.membrane.core.config.Proxy;
import com.predic8.membrane.core.config.Rules;
import com.predic8.membrane.core.rules.Rule;

public class Configuration extends AbstractXMLElement {

	public static final String ELEMENT_NAME = "configuration";

	private static final long serialVersionUID = 1L;

	// Control names
	public static final String ADJ_CONT_LENGTH = "auto_adjust_content_length";
	public static final String INDENT_MSG = "indent_message";
	public static final String ADJ_HOST_HEADER = "adjust_host_header_field";

	public static final String PROXY_HOST = "proxy_host";
	public static final String PROXY_PORT = "proxy_port";
	public static final String PROXY_USE = "proxy_use";

	public static final String TRACK_EXCHANGE = "autotrack_new_exchanges";

	private Collection<Rule> rules = new ArrayList<Rule>();

	public Map<String, Object> props = new HashMap<String, Object>();

	public Configuration() {

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

	public void setAdjustHostHeader(boolean status) {
		props.put(ADJ_HOST_HEADER, status);
	}

	public boolean getAdjustHostHeader() {
		if (props.containsKey(ADJ_HOST_HEADER)) {
			return (Boolean) props.get(ADJ_HOST_HEADER);
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

	public boolean getUseProxy() {
		if (props.containsKey(PROXY_USE)) {			
			return (Boolean) props.get(PROXY_USE);
		}
		return false;
	}
	
	public void setUseProxy(boolean status) {
		props.put(PROXY_USE, status);
	}
	
	public String getProxyHost() {
		if (props.containsKey(PROXY_HOST))
			return (String)props.get(PROXY_HOST);
	
		return null;
	}
	
	public String getProxyPort() {
		if (props.containsKey(PROXY_PORT))
			return (String)props.get(PROXY_PORT);
	
		return null;
	}
	
	public void setProxyHost(String host) {
		if (host == null)
			return;
		props.put(PROXY_HOST, host);
	}
	
	public void setProxyPort(String port) {
		props.put(PROXY_PORT, port);
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (Rules.ELEMENT_NAME.equals(child)) {
			rules = ((Rules) new Rules().parse(token)).getValues();
		} else if (Format.ELEMENT_NAME.equals(child)) {
			props.putAll(((Format) new Format().parse(token)).getValues());
		} else if (GUI.ELEMENT_NAME.equals(child)) {
			props.putAll(((GUI) new GUI().parse(token)).getValues());
		} else if (Proxy.ELEMENT_NAME.equals(child)) {
			props.putAll(((Proxy) new Proxy().parse(token)).getValues());
		}
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartDocument("UTF-8", "1.1");
		out.writeStartElement(ELEMENT_NAME);
		
		Rules childRules = new Rules();
		childRules.setValues(rules);
		childRules.write(out);
		
		Format childFormat = new Format();
		childFormat.setValues(props);
		childFormat.write(out);
		
		GUI childGui = new GUI();
		childGui.setValues(props);
		childGui.write(out);
		
		Proxy childProxy = new Proxy();
		childProxy.setValues(props);
		childProxy.write(out);
		
		out.writeEndElement();
		out.writeEndDocument();
	}

	
	public void copyFields(Configuration config) {
		setAdjustHostHeader(config.getAdjustHostHeader());
		setIndentMessage(config.getIndentMessage());
		setTrackExchange(config.getTrackExchange());
		setUseProxy(config.getUseProxy());
		setProxyHost(config.getProxyHost());
		setProxyPort(config.getProxyPort());
	}
	
}
