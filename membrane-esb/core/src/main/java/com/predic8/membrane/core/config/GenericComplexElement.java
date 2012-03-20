/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import java.util.*;

import javax.xml.stream.*;

public class GenericComplexElement extends AbstractXmlElement {

	private Map<String, String> attributes = new HashMap<String, String>();
	private String name;
	private AbstractXmlElement childParser;

	public GenericComplexElement() {
	}

	public GenericComplexElement(AbstractXmlElement p) {
		childParser = p;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (childParser != null) {
			childParser.parseChildren(token, child);
			return;
		}

		super.parseChildren(token, child);
	}

	@Override
	protected void parseAttributes(XMLStreamReader token)
			throws XMLStreamException {
		name = token.getLocalName();

		for (int i = 0; i < token.getAttributeCount(); i++) {
			attributes.put(token.getAttributeLocalName(i),
					token.getAttributeValue(i));
		}
	}

	public String getAttributeOrDefault(String name, String def) {
		if (attributes.containsKey(name))
			return attributes.get(name);
		return "" + def;
	}

	public String getAttribute(String name) {
		return attributes.get(name);
	}

	public AbstractXmlElement getChildParser() {
		return childParser;
	}

	public void setChildParser(AbstractXmlElement childParser) {
		this.childParser = childParser;
	}

	@Override
	protected String getElementName() {
		return name;
	}

}
