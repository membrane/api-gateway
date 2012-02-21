package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.WADLInterceptor;

public class WadlRewriterInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return WADLInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "wadlRewriter");

		setProperty(element, builder, "host");
		setProperty(element, builder, "protocol");
		setProperty(element, builder, "port");
	}

	private void setProperty(Element element, BeanDefinitionBuilder builder,
			String prop) {
		if (element.hasAttribute(prop))
			builder.addPropertyValue(prop, element.getAttribute(prop));
	}

}
