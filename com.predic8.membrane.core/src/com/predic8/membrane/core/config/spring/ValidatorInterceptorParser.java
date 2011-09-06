package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor;

public class ValidatorInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
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
		
		builder.setInitMethodName("init");
	}

}
