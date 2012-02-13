package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.RegExReplaceInterceptor;

public class RegExReplacerInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return RegExReplaceInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "regExReplacer");
		builder.addPropertyValue("pattern",element.getAttribute("regex"));
		builder.addPropertyValue("replacement",element.getAttribute("replace"));
	}

}
