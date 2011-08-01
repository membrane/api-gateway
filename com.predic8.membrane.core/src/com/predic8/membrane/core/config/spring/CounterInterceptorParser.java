package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.CountInterceptor;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;

public class CounterInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return CountInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "counter");
		
		builder.addPropertyValue("displayName", element.getAttribute("name"));
	}
	
}
