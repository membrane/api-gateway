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

import com.predic8.membrane.core.interceptor.statistics.StatisticsJDBCInterceptor;

public class StatisticsJDBCInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return StatisticsJDBCInterceptor.class;
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, parserContext, "statisticsJDBC");
		
		builder.addPropertyValue("postMethodOnly", element.getAttribute("postMethodOnly"));
		builder.addPropertyValue("soapOnly", element.getAttribute("soapOnly"));
		builder.addPropertyReference("dataSource", element.getAttribute("dataSource"));
	}

}
