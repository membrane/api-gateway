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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.AbstractConfigElement;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.SOAPProxy;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.util.TextUtil;

// TODO: remove MCElement declaration after proxies.xml has been removed
@MCElement(name="proxies", group="basic", xsd="" +
		"<xsd:sequence>\r\n" + 
		"</xsd:sequence>\r\n" + 
		"")
public class Proxies extends AbstractConfigElement {
	private Collection<Rule> rules = new ArrayList<Rule>();

	public Collection<Rule> getRules() {
		return rules;
	}

	public void setRules(Collection<Rule> rules) {
		this.rules = rules;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if ("serviceProxy".equals(child)) {
			rules.add(new ServiceProxy().parse(token));
		} else if ("soapProxy".equals(child)) {
			rules.add(new SOAPProxy().parse(token));
		} else if ("proxy".equals(child)) {
			rules.add(new ProxyRule().parse(token));
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartDocument(Constants.UTF_8, Constants.XML_VERSION);
		out.writeStartElement("proxies");

		for (Rule rule : rules) {
			rule.write(out);
		}

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
