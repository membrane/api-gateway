package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.statistics.StatisticsCSVInterceptor;

public class StatisticsCSVInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return StatisticsCSVInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "statisticsCSV");
		
		builder.addPropertyValue("fileName", element.getAttribute("file"));
	}
	
}
