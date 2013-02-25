/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.config.spring;

import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.collect.Sets;

public abstract class AbstractParser extends AbstractSingleBeanDefinitionParser {

	private static final String MEMBRANE_BEANS_NAMESPACE = "http://membrane-soa.org/router/beans/1/";
	private static final String MEMBRANE_PROXIES_NAMESPACE = "http://membrane-soa.org/schemas/proxies/v1/";

	private boolean inlined = false;

	public BeanDefinition parse(Element e) {
		inlined = true;
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(getBeanClass(e));
		doParse(e, builder);
		return builder.getBeanDefinition();
	}

	protected void setIdIfNeeded(Element element, ParserContext parserContext, String defaultId) {
		if ( !isInlined() && !element.hasAttribute("id") ) {
			Set<String> names = Sets.newHashSet(parserContext.getRegistry().getBeanDefinitionNames());
			for (int i = 0; ; i++) {
				String id = defaultId + (i == 0 ? "" : i);
				if (!names.contains(id)) {
					element.setAttribute("id", id);
					return;
				}
			}
		}
	}

	protected boolean isInlined() {		
		return inlined ;
	}	

	protected void setProperty(String prop, Element e, BeanDefinitionBuilder builder) {
		setProperty(prop, prop, e, builder);
	}
	
	protected void setProperty(String prop, Element e, BeanDefinitionBuilder builder, boolean flexibleEnum) {
		setProperty(prop, prop, e, builder, flexibleEnum);
	}

	protected void setProperty(String xmlPropertyName, String springPropertyName, Element e, BeanDefinitionBuilder builder) {
		setProperty(xmlPropertyName, springPropertyName, e, builder, false);
	}
	
	protected void setProperty(String xmlPropertyName, String springPropertyName, Element e, BeanDefinitionBuilder builder, boolean flexibleEnum) {
		String value = e.getAttribute(xmlPropertyName);
		if (flexibleEnum)
			value = value.toUpperCase();
		builder.addPropertyValue(springPropertyName, value);
	}

	protected void setPropertyIfSet(String prop, Element e, BeanDefinitionBuilder builder) {
		setPropertyIfSet(prop, prop, e, builder);
	}

	protected void setPropertyIfSet(String prop, Element e, BeanDefinitionBuilder builder, boolean flexibleEnum) {
		setPropertyIfSet(prop, prop, e, builder, flexibleEnum);
	}
	
	protected void setPropertyIfSet(String xmlPropertyName, String springPropertyName, Element e, BeanDefinitionBuilder builder) {
		setPropertyIfSet(xmlPropertyName, springPropertyName, e, builder, false);
	}

	protected void setPropertyIfSet(String xmlPropertyName, String springPropertyName, Element e, BeanDefinitionBuilder builder, boolean flexibleEnum) {
		if (e.hasAttribute(xmlPropertyName))
			setProperty(xmlPropertyName, springPropertyName, e, builder, flexibleEnum);
	}
	
	protected void parseElementToProperty(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder, String property) {
		BeanDefinitionParserDelegate delegate = parserContext.getDelegate();

		if (delegate.isDefaultNamespace(ele)) {
			Object o = delegate.parsePropertySubElement(ele, builder.getBeanDefinition());
			builder.addPropertyValue(property, o);
		} else {
			BeanDefinition bd = delegate.parseCustomElement(ele);
			builder.addPropertyValue(property, bd);
		}
	}
	
	protected void handleChildObject(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder, Class<?> clazz, Object child) {
		throw new RuntimeException("Do not know how to handle child of class " + clazz + ". Please override handleChildObject().");
	}
	
	protected void parseChildren(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		NodeList nl = element.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element) {
				handleChildElement((Element) node, parserContext, builder);
			}
		}
	}
	
	protected void handleChildElement(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder) {
		BeanDefinitionParserDelegate delegate = parserContext.getDelegate();

		try {
			if (delegate.isDefaultNamespace(ele)) {
				Object o = delegate.parsePropertySubElement(ele, builder.getBeanDefinition());

				String clazz = null;
				if (o instanceof BeanDefinitionHolder) {
					clazz = ((BeanDefinitionHolder) o).getBeanDefinition().getBeanClassName();
				} else if (o instanceof RuntimeBeanReference) {
					clazz = parserContext.getRegistry().getBeanDefinition(((RuntimeBeanReference) o).getBeanName()).getBeanClassName();
				} else {
					parserContext.getReaderContext().error("Don't know how to get bean class from " + o.getClass(), ele);
				}

				handleChildObject(ele, parserContext, builder, Class.forName(clazz), o);
			} else {
				BeanDefinition bd = delegate.parseCustomElement(ele);

				handleChildObject(ele, parserContext, builder, Class.forName(bd.getBeanClassName()), bd);
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected int incrementCounter(BeanDefinitionBuilder builder, String counter) {
		Integer i = (Integer) builder.getRawBeanDefinition().getAttribute(counter);
		if (i == null)
			i = 0;
		builder.getRawBeanDefinition().setAttribute(counter, i+1);
		return i;
	}
	
	protected boolean isMembraneNamespace(String namespace) {
		return MEMBRANE_BEANS_NAMESPACE.equals(namespace) ||
				MEMBRANE_PROXIES_NAMESPACE.equals(namespace);
	}

}
