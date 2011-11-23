package com.predic8.membrane.core.config.spring;

import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.rewrite.ReverseProxyingInterceptor;

public class ReverseProxyingInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return ReverseProxyingInterceptor.class;
	}

}
