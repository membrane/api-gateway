package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.RegExReplaceInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;

public class GroovyInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return GroovyInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "groovy");
		builder.addPropertyValue("src",element.getTextContent());
	}

}
