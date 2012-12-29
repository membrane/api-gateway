package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.authentication.session.LoginInterceptor;

public class LoginInterceptorParser extends AbstractParser {
	
	@Override
	protected Class<?> getBeanClass(Element element) {
		return LoginInterceptor.class;
	}
	
	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setProperty("path", element, builder);
		setProperty("location", element, builder);
		
		// TODO
	}

}
