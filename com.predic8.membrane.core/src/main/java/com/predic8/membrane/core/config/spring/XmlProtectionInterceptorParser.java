package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionInterceptor;

public class XmlProtectionInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return XMLProtectionInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "xmlProtection");
		
		setProperty(element, builder, "removeDTD");
		setProperty(element, builder, "maxElementNameLength");
		setProperty(element, builder, "maxAttibuteCount");
	}

	private void setProperty(Element element, BeanDefinitionBuilder builder, String prop) {
		if (element.hasAttribute(prop) ) builder.addPropertyValue(prop, element.getAttribute(prop));
	}
	
}
