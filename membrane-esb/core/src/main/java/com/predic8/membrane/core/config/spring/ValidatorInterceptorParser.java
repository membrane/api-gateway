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
		
		builder.addPropertyReference("router", "router");
		
		builder.setInitMethodName("init");
	}

}
