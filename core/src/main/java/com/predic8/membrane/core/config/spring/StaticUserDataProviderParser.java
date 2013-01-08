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

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;

public class StaticUserDataProviderParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return StaticUserDataProvider.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "staticUserDataProvider");
		builder.addPropertyValue("users", getUsers(element));
	}

	private Map<String, Map<String, String>> getUsers(Element e) {
		Map<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();
		NodeList childs = e.getChildNodes();
		for (int i = 0; i < childs.getLength(); i++) {
			if (childs.item(i) instanceof Element) {
				Element f = (Element)childs.item(i);
				Map<String, String> user = new HashMap<String, String>();
				for (int j = 0; j < f.getAttributes().getLength(); j++) {
					Node item = f.getAttributes().item(j);
					user.put(item.getLocalName(), ((org.w3c.dom.Attr)item).getValue());
				}
				
				result.put(user.get("username"), user);
			}
		}
		return result;
	}
}
