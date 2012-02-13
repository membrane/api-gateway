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

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Router;

public class Global extends AbstractConfigElement {

	public static final String INDENT_MSG = "indent_message";
	
	public static final String ADJ_HOST_HEADER = "adjust_host_header_field";
	
	public static final String TRACK_EXCHANGE = "autotrack_new_exchanges";
	
	public static final String ADJ_CONT_LENGTH = "auto_adjust_content_length";
	
	
	public static final String ATTRIBUTE_INDENT_MSG = "indentMessage"; 
	
	public static final String ATTRIBUTE_AUTO_TRACK = "autoTrack"; 
	
	public static final String ATTRIBUTE_ADJ_HOST_HEADER = "adjustHostHeader"; 
	
	public static final String ATTRIBUTE_ADJ_CONTENT_LENGTH = "adjustContentLength"; 
	
	
	public Map<String, Object> values = new HashMap<String, Object>();

	private ProxyConfiguration proxyConfiguration;

	public Global(Router router) {
		super(router);
		setIndentMessage(true);
		setAdjustHostHeader(true);
		setTrackExchange(false);
		setAdjustContentLength(true);
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {

		if ("router".equals(child)) {
			GenericComplexElement r = new GenericComplexElement();
			r.parse(token);
			values.put(ADJ_HOST_HEADER, Boolean.parseBoolean(r.getAttribute(ATTRIBUTE_ADJ_HOST_HEADER)));
			values.put(ADJ_CONT_LENGTH, Boolean.parseBoolean(r.getAttribute(ATTRIBUTE_ADJ_CONTENT_LENGTH)));
		} else if ("monitor-gui".equals(child)) {
			GenericComplexElement r = new GenericComplexElement();
			r.parse(token);
			values.put(TRACK_EXCHANGE, Boolean.parseBoolean(r.getAttribute(ATTRIBUTE_AUTO_TRACK)));
			values.put(INDENT_MSG, Boolean.parseBoolean(r.getAttribute(ATTRIBUTE_INDENT_MSG)));
		} else if ("proxyConfiguration".equals(child)) {
			proxyConfiguration = (ProxyConfiguration) new ProxyConfiguration(
					router).parse(token);
		}
	}

	public Map<String, Object> getValues() {
		return values;
	}

	public void setValues(Map<String, Object> values) {
		this.values = values;
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("global");

		out.writeStartElement("router");
		out.writeAttribute(ATTRIBUTE_ADJ_HOST_HEADER, "" + values.get(ADJ_HOST_HEADER));
		out.writeAttribute(ATTRIBUTE_ADJ_CONTENT_LENGTH, "" + values.get(ADJ_CONT_LENGTH));
		out.writeEndElement();
		out.writeStartElement("monitor-gui");
		out.writeAttribute(ATTRIBUTE_INDENT_MSG, "" + values.get(INDENT_MSG));
		out.writeAttribute(ATTRIBUTE_AUTO_TRACK, "" + values.get(TRACK_EXCHANGE));
		out.writeEndElement();

		if (proxyConfiguration != null) {
			proxyConfiguration.write(out);
		}

		out.writeEndElement();
	}

	public ProxyConfiguration getProxyConfiguration() {
		return proxyConfiguration;
	}

	public void setProxyConfiguration(ProxyConfiguration proxyConfiguration) {
		this.proxyConfiguration = proxyConfiguration;
	}
	
	public void setIndentMessage(boolean status) {
		values.put(INDENT_MSG, status);
	}

	public boolean getIndentMessage() {
		if (values.containsKey(INDENT_MSG)) {
			return (Boolean)values.get(INDENT_MSG);
		}
		return false;
	}
	
	public void setAdjustHostHeader(boolean status) {
		values.put(ADJ_HOST_HEADER, status);
	}
	
	public boolean getAdjustHostHeader() {
		if (values.containsKey(ADJ_HOST_HEADER)) {
			return (Boolean) values.get(ADJ_HOST_HEADER);
		}
		return true;
	}
	
	public void setTrackExchange(boolean status) {
		values.put(TRACK_EXCHANGE, status);
	}

	public boolean getTrackExchange() {
		if (values.containsKey(TRACK_EXCHANGE)) {
			return (Boolean)values.get(TRACK_EXCHANGE);
		}
		return false;
	}
	
	public void setAdjustContentLength(boolean status) {
		values.put(ADJ_CONT_LENGTH, status);
	}

	public boolean getAdjustContentLength() {
		if (values.containsKey(ADJ_CONT_LENGTH)) {
			return (Boolean) values.get(ADJ_CONT_LENGTH);
		}
		return false;
	}

}
