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
import org.w3c.dom.Element;

import com.predic8.membrane.core.ConfigurationManager;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Global;

public class RouterParser extends
		AbstractSingleBeanDefinitionParser {

	protected Class<?> getBeanClass(Element element) {
		return Router.class;
	}

	protected void doParse(Element e, BeanDefinitionBuilder bean) {
		e.setAttribute("id", "router");
		bean.addPropertyReference("transport", "transport");

		if (e.hasAttribute("exchangeStore")) {
			bean.addPropertyReference("exchangeStore", e.getAttribute("exchangeStore"));
		}
		
		ConfigurationManager cm = new ConfigurationManager();
		cm.setHotDeploy(Boolean.parseBoolean(e.getAttribute("hotDeploy")));
		if (e.hasAttribute(Global.ATTRIBUTE_ADJ_HOST_HEADER))
			cm.getProxies().setAdjustHostHeader(Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_ADJ_HOST_HEADER)));
		else
			cm.getProxies().setAdjustHostHeader(true);
		cm.getProxies().setIndentMessage(Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_INDENT_MSG)));
		cm.getProxies().setAdjustContentLength(Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_ADJ_CONTENT_LENGTH)));
		cm.getProxies().setTrackExchange(Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_AUTO_TRACK)));
		bean.addPropertyValue("configurationManager", cm);
	}
}