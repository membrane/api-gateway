package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.ThrottleInterceptor;

public class ThrottleInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return ThrottleInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element,"throttle");

		builder.addPropertyValue("delay", element.getAttribute("delay"));
		builder.addPropertyValue("maxThreads", element.getAttribute("maxThreads"));
		builder.addPropertyValue("busyDelay", element.getAttribute("busyDelay"));
	}
}
