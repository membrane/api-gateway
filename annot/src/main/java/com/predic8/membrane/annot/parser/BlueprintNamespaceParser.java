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

import com.predic8.membrane.annot.MCMain;

/**
 * Base class for auto-generated blueprint parsers for {@link MCMain}s (=XML namespaces).
 */
public abstract class BlueprintNamespaceParser implements BlueprintParser {

	HashMap<String, BlueprintParser> parsers = new HashMap<String, BlueprintParser>();
	
	public BlueprintNamespaceParser() {
		init();
	}

	public abstract void init();
	
	protected void registerGlobalBeanDefinitionParser(String elementName, BlueprintParser parser) {
		parsers.put(elementName, parser);
	}

	protected void registerLocalBeanDefinitionParser(String parentBeanClassName, String elementName, BlueprintParser parser) {
		// TODO
	}
	
	@Override
	public Metadata parse(BlueprintParser globalParser, Element element,
			ParserContext context) {
		BlueprintParser parser = parsers.get(element.getNodeName());
		if (parser == null)
			throw new RuntimeException("No parser declared for element <" + element.getNodeName() + ">.");
		return parser.parse(globalParser, element, context);
	}

}
