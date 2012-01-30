package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.exchangestore.*;

public class ForgetfulExchangeStoreParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return ForgetfulExchangeStore.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "forgetfulExchangeStore");
	}
	
}
