package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.LogInterceptor;

public class LogInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return LogInterceptor.class;
	}
	
	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element,"log");
		builder.addPropertyValue("headerOnly", Boolean.parseBoolean(element.getAttribute("headerOnly")));
	}

}
