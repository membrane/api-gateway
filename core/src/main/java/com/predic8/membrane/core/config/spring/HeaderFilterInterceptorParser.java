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
import java.util.List;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.predic8.membrane.core.interceptor.HeaderFilterInterceptor;
import com.predic8.membrane.core.interceptor.HeaderFilterInterceptor.Action;
import com.predic8.membrane.core.interceptor.HeaderFilterInterceptor.Rule;

public class HeaderFilterInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return HeaderFilterInterceptor.class;
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, parserContext, "filterHeader");
		builder.addPropertyValue("rules", getRules(element));
	}

	private List<Rule> getRules(Element e) {
		List<Rule> m = new ArrayList<Rule>();
		NodeList childs = e.getChildNodes();
		for (int i = 0; i < childs.getLength(); i++) {
			if (childs.item(i) instanceof Element) {
				Element f = (Element)childs.item(i);
				String text = f.getTextContent();
				
				Rule r = null;
				if (f.getTagName().equals("include"))
					r = new HeaderFilterInterceptor.Rule(text, Action.KEEP);
				else if (f.getTagName().equals("exclude"))
					r = new HeaderFilterInterceptor.Rule(text, Action.REMOVE);
				else
					throw new RuntimeException("Unknown child element of <filterHeader />: '" + f.getTagName() + "'");

				m.add(r);
			}
		}
		return m;
	}
}
