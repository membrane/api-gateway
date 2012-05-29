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

package com.predic8.membrane.servlet.embedded;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.config.spring.HttpTransportParser;

public class ServletTransportParser extends
		HttpTransportParser {

	protected Class<?> getBeanClass(Element element) {
		return ServletTransport.class;
	}

	protected void doParse(Element e, BeanDefinitionBuilder bean) {
		e.setAttribute("id", "transport");
		
		bean.addPropertyValue("httpClientRetries", e.getAttribute("httpClientRetries"));
		bean.addPropertyValue("printStackTrace", Boolean.parseBoolean(e.getAttribute("printStackTrace")));
		bean.addPropertyValue("removeContextRoot", e.getAttribute("removeContextRoot").length() == 0 ? true : Boolean.parseBoolean(e.getAttribute("removeContextRoot")));
		
		bean.addPropertyValue("interceptors", parseInterceptors(e));
	}
}