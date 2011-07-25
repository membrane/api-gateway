package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.schemavalidation.SOAPMessageValidatorInterceptor;

public class SoapValidatorInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return SOAPMessageValidatorInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element,"soapValidator");
		
		builder.addPropertyValue("wsdl", element.getAttribute("wsdl"));
		builder.setInitMethodName("init");
	}

}
