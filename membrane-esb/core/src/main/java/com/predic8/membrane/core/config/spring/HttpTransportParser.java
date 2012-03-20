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

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.predic8.membrane.core.transport.http.HttpTransport;

public class HttpTransportParser extends
		AbstractSingleBeanDefinitionParser {

	protected Class<?> getBeanClass(Element element) {
		return HttpTransport.class;
	}

	protected void doParse(Element e, BeanDefinitionBuilder bean) {
		e.setAttribute("id", "transport");

		bean.addPropertyValue("coreThreadPoolSize", e.getAttribute("coreThreadPoolSize"));
		bean.addPropertyValue("socketTimeout", e.getAttribute("socketTimeout"));
		bean.addPropertyValue("httpClientRetries", e.getAttribute("httpClientRetries"));
		bean.addPropertyValue("tcpNoDelay", e.getAttribute("tcpNoDelay"));
		bean.addPropertyValue("autoContinue100Expected", e.getAttribute("autoContinue100Expected"));
		
		bean.addPropertyValue("interceptors", parseInterceptors(e));
	}

	private List<BeanDefinition> parseInterceptors(Element e) {
		
		List<BeanDefinition> l = new ManagedList<BeanDefinition>();
		NodeList nL = e.getChildNodes();

		for (int i = 0; i < nL.getLength(); i++) {
			Node n = nL.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				l.add(getInterceptorParser((Element) n).parse((Element) n));
			}
		}
		return l;
	}

	private AbstractParser getInterceptorParser(Element e)  {
		try {
			return (AbstractParser) Class.forName("com.predic8.membrane.core.config.spring."+StringUtils.capitalize(e.getLocalName())+"InterceptorParser").newInstance();
		} catch (Exception e1) {
			throw new RuntimeException(e1);			
		}
	}
}