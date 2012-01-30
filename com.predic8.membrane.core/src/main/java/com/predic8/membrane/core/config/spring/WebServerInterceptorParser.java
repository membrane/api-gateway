package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;

public class WebServerInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return WebServerInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "webServer");
		
		builder.addPropertyValue("docBase", element.getAttribute("docBase"));
	}
	
}
