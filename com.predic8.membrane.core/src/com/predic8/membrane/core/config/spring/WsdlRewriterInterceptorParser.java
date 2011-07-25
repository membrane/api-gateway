package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.WSDLInterceptor;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;

public class WsdlRewriterInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return WSDLInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "wsdlRewriter");
		
		setProperty(element, builder, "host");
		setProperty(element, builder, "protocol");
		setProperty(element, builder, "port");
		setProperty(element, builder, "registryWSDLRegisterURL");
	}

	private void setProperty(Element element, BeanDefinitionBuilder builder, String prop) {
		if (element.hasAttribute(prop) ) builder.addPropertyValue(prop, element.getAttribute(prop));
	}
	
}
