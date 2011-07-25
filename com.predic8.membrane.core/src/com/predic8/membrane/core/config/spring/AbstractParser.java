package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.w3c.dom.Element;

public class AbstractParser extends AbstractSingleBeanDefinitionParser {

	private boolean inlined = false;

	public BeanDefinition parse(Element e) {
		inlined = true;
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(getBeanClass(e));
		doParse(e, builder);
		return builder.getBeanDefinition();
	}
	
	protected void setIdIfNeeded(Element element, String defaultId) {
		if ( !isInlined() && !element.hasAttribute("id") ) element.setAttribute("id", defaultId);
	}

	protected boolean isInlined() {		
		return inlined ;
	}	
}
