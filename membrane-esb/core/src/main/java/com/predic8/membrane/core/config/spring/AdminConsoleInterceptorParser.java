package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;

public class AdminConsoleInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return AdminConsoleInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "adminConsole");
	}
	
}
