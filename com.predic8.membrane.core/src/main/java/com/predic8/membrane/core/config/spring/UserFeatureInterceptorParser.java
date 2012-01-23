package com.predic8.membrane.core.config.spring;

import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.UserFeatureInterceptor;

public class UserFeatureInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return UserFeatureInterceptor.class;
	}
}
