/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandler;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AbstractNamespaceHandler implements NamespaceHandler {

	private final Map<String, BeanDefinitionParser> parsers = new HashMap<String, BeanDefinitionParser>();

	private final Map<String, Map<String, BeanDefinitionParser>> localParsers = new HashMap<String, Map<String,BeanDefinitionParser>>();

	@Override
	public void init() {
		// do nothing
	}

	@Override
	public BeanDefinitionHolder decorate(Node source, BeanDefinitionHolder definition, ParserContext parserContext) {
		return definition;
	}

	public void registerGlobalBeanDefinitionParser(String elementName, BeanDefinitionParser parser) {
		this.parsers.put(elementName, parser);
	}

	public void registerLocalBeanDefinitionParser(String parentBeanClassName, String elementName, BeanDefinitionParser parser) {
		Map<String, BeanDefinitionParser> lp = localParsers.get(parentBeanClassName);
		if (lp == null) {
			lp = new HashMap<String, BeanDefinitionParser>();
			localParsers.put(parentBeanClassName, lp);
		}
		lp.put(elementName, parser);
	}

	private BeanDefinitionParser findParserForElement(Element element, ParserContext parserContext) {
		String localName = parserContext.getDelegate().getLocalName(element);

		if (parserContext.getContainingBeanDefinition() != null) {
			String beanClassName = parserContext.getContainingBeanDefinition().getBeanClassName();
			Map<String, BeanDefinitionParser> parentLocalParsers = localParsers.get(beanClassName);
			if (parentLocalParsers != null) {
				BeanDefinitionParser parser = parentLocalParsers.get(localName);
				if (parser != null)
					return parser;
			}
		}

		BeanDefinitionParser parser = this.parsers.get(localName);
		if (parser == null) {
			parserContext.getReaderContext().fatal("Cannot locate BeanDefinitionParser for element [" + localName + "]", element);
		}
		return parser;
	}

	public BeanDefinition parse(Element element, ParserContext parserContext) {

		return findParserForElement(element, parserContext).parse(element, parserContext);
	}



}
