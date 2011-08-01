package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.interceptor.balancer.ClusterNotificationInterceptor;

public class ClusterNotificationInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return ClusterNotificationInterceptor.class;
	}

	@Override
	protected void doParse(Element e, BeanDefinitionBuilder builder) {
		setIdIfNeeded(e, "clusterNotification");
		
		setPropertyIfSet("validateSignature", e, builder);
		setPropertyIfSet("keyHex", e, builder);
		setPropertyIfSet("timeout", e, builder);				
	}

	private void setPropertyIfSet(String prop, Element e, BeanDefinitionBuilder builder) {
		if (e.hasAttribute(prop)) {
			builder.addPropertyValue(prop, e.getAttribute(prop));
		}
	}
	
}
