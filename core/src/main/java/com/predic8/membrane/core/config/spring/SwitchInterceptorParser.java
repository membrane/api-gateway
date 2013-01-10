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

import java.util.*;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.cbr.*;

public class SwitchInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return XPathCBRInterceptor.class;
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, parserContext, "switch");
		builder.addPropertyValue("routeProvider",getRouteProvider(element));
	}

	private RouteProvider getRouteProvider(Element e) {
		DefaultRouteProvider p = new DefaultRouteProvider();
		p.setRoutes(getRoutes(e));
		return p;
	}

	private List<Case> getRoutes(Element e) {
		List<Case> m = new ArrayList<Case>();
		for (Element mapping : DomUtils.getChildElementsByTagName(e, "case")) {
			m.add( new Case(mapping.getAttribute("xPath"), mapping.getAttribute("url"))); 
		}
		return m;
	}
}
