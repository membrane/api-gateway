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
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import com.predic8.membrane.core.transport.http.HttpTransport;

public class HttpTransportParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return HttpTransport.class;
	}

	protected void doParse(Element e, ParserContext parserContext, BeanDefinitionBuilder bean) {
		e.setAttribute("id", "transport");

		bean.addPropertyValue("coreThreadPoolSize", e.getAttribute("coreThreadPoolSize"));
		bean.addPropertyValue("socketTimeout", e.getAttribute("socketTimeout"));
		bean.addPropertyValue("httpClientRetries", e.getAttribute("httpClientRetries"));
		bean.addPropertyValue("tcpNoDelay", e.getAttribute("tcpNoDelay"));
		bean.addPropertyValue("printStackTrace", Boolean.parseBoolean(e.getAttribute("printStackTrace")));
		bean.addPropertyValue("autoContinue100Expected", e.getAttribute("autoContinue100Expected").length() == 0 ? true : Boolean.parseBoolean(e.getAttribute("autoContinue100Expected")));
		
		parseChildren(e, parserContext, bean);
	}
	
	@Override
	protected void handleChildObject(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder,
			Class<?> clazz, Object child) {
		builder.addPropertyValue("interceptors[" + incrementCounter(builder, "interceptor") + "]", child);
	}

}