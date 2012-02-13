package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.exchangestore.*;

public class FileExchangeStoreParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return FileExchangeStore.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "fileExchangeStore");
		
		builder.addPropertyValue("saveBodyOnly", element.getAttribute("saveBodyOnly"));
		builder.addPropertyValue("raw", element.getAttribute("raw"));
		builder.addPropertyValue("dir", element.getAttribute("dir"));
	}
	
}
