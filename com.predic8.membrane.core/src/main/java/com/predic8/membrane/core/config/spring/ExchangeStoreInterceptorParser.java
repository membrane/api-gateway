package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.*;

public class ExchangeStoreInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return ExchangeStoreInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		builder.addPropertyReference("exchangeStore", element.getAttribute("name"));
	}
}
