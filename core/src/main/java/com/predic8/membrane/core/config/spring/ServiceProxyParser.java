package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class ServiceProxyParser extends AbstractParser {

	@Override
	protected Class<?> getBeanClass(Element element) {
		return ServiceProxy.class;
	}
	
	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		super.doParse(element, builder);

		// TODO

		
		// path
		// ssl
		// request/reponse/interceptor
		// target
		
		

		/*
		
		"							<xsd:attribute name=\"name\" type=\"xsd:string\" />\r\n" + 
		"							<xsd:attribute name=\"port\" type=\"xsd:int\" />\r\n" + 
		"							<xsd:attribute name=\"blockResponse\" type=\"xsd:boolean\" />\r\n" + 
		"							<xsd:attribute name=\"blockRequest\" type=\"xsd:boolean\" />\r\n" + 
		"							<xsd:attribute name=\"host\" type=\"xsd:int\" />\r\n" + 
		"							<xsd:attribute name=\"method\" type=\"xsd:string\" />\r\n" + 
		"							<xsd:attribute name=\"ip\" type=\"xsd:string\" />\r\n" + 
		 	*/
		
		builder.addConstructorArgValue(
				new ServiceProxyKey(Integer.parseInt(element.getAttribute("port"))));
		builder.addConstructorArgValue("www.predic8.com");
		builder.addConstructorArgValue(80);

	}
}
