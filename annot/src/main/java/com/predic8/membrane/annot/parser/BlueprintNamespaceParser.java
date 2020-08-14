/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.parser;

import java.util.HashMap;

import org.apache.aries.blueprint.ParserContext;
import org.osgi.service.blueprint.reflect.Metadata;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.predic8.membrane.annot.MCMain;

/**
 * Base class for auto-generated blueprint parsers for {@link MCMain}s (=XML namespaces).
 */
public abstract class BlueprintNamespaceParser implements BlueprintParser {

	public static final String KEY_PARENT_CLASS_NAME = "parentClass";

	HashMap<String, BlueprintParser> parsers = new HashMap<String, BlueprintParser>();
	HashMap<String, HashMap<String, BlueprintParser>> localParsers = new HashMap<String, HashMap<String, BlueprintParser>>();


	public BlueprintNamespaceParser() {
		init();
	}

	public abstract void init();

	protected void registerGlobalBeanDefinitionParser(String elementName, BlueprintParser parser) {
		parsers.put(elementName, parser);
	}

	protected void registerLocalBeanDefinitionParser(String parentBeanClassName, String elementName, BlueprintParser parser) {
		HashMap<String, BlueprintParser> lp = localParsers.get(parentBeanClassName);
		if (lp == null) {
			lp = new HashMap<String, BlueprintParser>();
			localParsers.put(parentBeanClassName, lp);
		}
		lp.put(elementName, parser);
	}

	@Override
	public Metadata parse(BlueprintParser globalParser, Element element,
			ParserContext context) {
		Node node = element.getParentNode();
		if (node != null) {
			String parentClass = (String) node.getUserData(KEY_PARENT_CLASS_NAME);
			if (parentClass != null) {
				HashMap<String, BlueprintParser> parentLocalParsers = localParsers.get(parentClass);
				if (parentLocalParsers != null) {
					BlueprintParser parser = parentLocalParsers.get(element.getNodeName());
					if (parser != null) {
						return parser.parse(globalParser, element, context);
					}
				}
			}
		}

		BlueprintParser parser = parsers.get(element.getNodeName());
		if (parser == null)
			throw new RuntimeException("No parser declared for element <" + element.getNodeName() + ">.");
		return parser.parse(globalParser, element, context);
	}

}
