package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;

public class AccessControlInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return AccessControlInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "accessControl");
		
		builder.addPropertyValue("aclFilename", element.getAttribute("file"));
		builder.setInitMethodName("init");
	}
	
}
