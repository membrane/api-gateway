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

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Global;

public class RouterParser extends
		AbstractSingleBeanDefinitionParser {

	protected Class<?> getBeanClass(Element element) {
		return Router.class;
	}

	protected void doParse(Element e, ParserContext parserContext, BeanDefinitionBuilder bean) {
		e.setAttribute("id", "router");
		bean.addPropertyReference("transport", "transport");

		if (e.hasAttribute("exchangeStore")) {
			bean.addPropertyReference("exchangeStore", e.getAttribute("exchangeStore"));
		}
		
		bean.addPropertyValue("configurationManager.hotDeploy", Boolean.parseBoolean(e.getAttribute("hotDeploy")));
		bean.addPropertyValue("configurationManager.proxies.adjustHostHeader", 
				e.hasAttribute(Global.ATTRIBUTE_ADJ_HOST_HEADER) ?
						Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_ADJ_HOST_HEADER)) : true);
		bean.addPropertyValue("configurationManager.proxies.indentMessage", 
				Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_INDENT_MSG)));
		bean.addPropertyValue("configurationManager.proxies.adjustContentLength", 
				Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_ADJ_CONTENT_LENGTH)));
		bean.addPropertyValue("configurationManager.proxies.trackExchange",
				Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_AUTO_TRACK)));
		
	}
}