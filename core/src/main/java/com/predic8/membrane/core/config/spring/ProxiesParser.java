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

package com.predic8.membrane.core.config.spring;

import java.util.ArrayList;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import com.predic8.membrane.core.Proxies;
import com.predic8.membrane.core.rules.Rule;

public class ProxiesParser extends
		AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return Proxies.class;
	}

	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, parserContext, "proxies");

		builder.addPropertyValue("rules", new ArrayList<Object>());
		
		parseChildren(element, parserContext, builder);
	}
	
	@Override
	protected void handleChildObject(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder,
			Class<?> clazz, Object child) {
		if (Rule.class.isAssignableFrom(clazz)) {
			builder.addPropertyValue("rules[" + incrementCounter(builder, "rules") + "]", child);
		} else {
			throw new RuntimeException("Don't know what to do with " + clazz + ".");
		}
	}
}