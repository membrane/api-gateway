package com.predic8.membrane.core.config.spring;

import java.util.*;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor;

public class BasicAuthenticationInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return BasicAuthenticationInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "basicAuthentication");
		builder.addPropertyValue("users",getUsers(element));
	}

	private Map<String, String> getUsers(Element e) {
		Map<String, String> users = new HashMap<String, String>();
		for (Element user : DomUtils.getChildElementsByTagName(e, "user")) {
			users.put(user.getAttribute("name"), user.getAttribute("password"));
		}
		return users;
	}
}
