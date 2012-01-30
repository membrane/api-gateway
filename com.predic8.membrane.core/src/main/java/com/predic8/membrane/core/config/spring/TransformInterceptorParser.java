package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptor;

public class TransformInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return XSLTInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element,"transform");
		builder.addPropertyValue("xslt", element.getAttribute("xslt"));
		builder.addPropertyValue("concurrency", element.getAttribute("concurrency"));
	}
}
