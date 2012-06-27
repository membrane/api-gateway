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
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor;

public class ValidatorInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return ValidatorInterceptor.class;
	}

	@Override
	protected void doParse(Element e, BeanDefinitionBuilder builder) {
		setIdIfNeeded(e,"validator");
		
		if (e.hasAttribute("wsdl")) {
			builder.addPropertyValue("wsdl", e.getAttribute("wsdl"));			
		}
		if (e.hasAttribute("schema")) {
			builder.addPropertyValue("schema", e.getAttribute("schema"));			
		}
		if (e.hasAttribute("jsonSchema")) {
			builder.addPropertyValue("jsonSchema", e.getAttribute("jsonSchema"));
		}
		if (e.hasAttribute("schematron")) {
			builder.addPropertyValue("schematron", e.getAttribute("schematron"));
		}
		if (e.hasAttribute("failureHandler")) {
			builder.addPropertyValue("failureHandler", e.getAttribute("failureHandler"));
		}
		builder.addPropertyValue("skipFaults", e.getAttribute("skipFaults"));
	}

}
