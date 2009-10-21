package com.predic8.membrane.core.config;

import java.util.ArrayList;
import java.util.Collection;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;

public class Rules extends XMLElement {

	public static final String ELEMENT_NAME = "rules";

	private Collection<Rule> rules = new ArrayList<Rule>();

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (ForwardingRule.ELEMENT_NAME.equals(child)) {
			rules.add((ForwardingRule) new ForwardingRule().parse(token));
		} else if (ProxyRule.ELEMENT_NAME.equals(child)) {
			rules.add((ProxyRule) new ProxyRule().parse(token));
		}

	}

	public Collection<Rule> getValues() {
		return rules;
	}

	public void setValues(Collection<Rule> values) {
		this.rules = values;
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		for (Rule rule : rules) {
			rule.write(out);
		}
		out.writeEndElement();
	}

}
