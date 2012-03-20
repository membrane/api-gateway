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
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor.Mapping;

public class Rest2SoapInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return REST2SOAPInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "rest2Soap");
		builder.addPropertyValue("mappings",getMappings(element));
	}

	private List<Mapping> getMappings(Element e) {
		List<Mapping> mappings = new ArrayList<Mapping>();
		for (Element mapping : DomUtils.getChildElementsByTagName(e, "mapping")) {
			Mapping m = new Mapping();
			m.regex = mapping.getAttribute("regex");
			m.soapAction = mapping.getAttribute("soapAction");
			m.soapURI = mapping.getAttribute("soapURI");
			m.requestXSLT = mapping.getAttribute("requestXSLT");
			m.responseXSLT = mapping.getAttribute("responseXSLT");
			m.responseType = mapping.getAttribute("responseType");
			mappings.add(m);
		}
		return mappings;
	}
}
