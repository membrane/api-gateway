package com.predic8.membrane.core.config.spring;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;

public class ProxyParser extends AbstractProxyParser {

	@Override
	protected Class<?> getBeanClass(Element element) {
		return ProxyRule.class;
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		super.doParse(element, parserContext, builder);
		
		setIdIfNeeded(element, parserContext, "proxy");

		String name = StringUtils.defaultIfEmpty(element.getAttribute("name"), null);
		int port = Integer.parseInt(StringUtils.defaultIfEmpty(element.getAttribute("port"), "80"));
		String ip = StringUtils.defaultIfEmpty(element.getAttribute("ip"), null);
		boolean blockRequest = Boolean.parseBoolean(element.getAttribute("blockRequest"));
		boolean blockResponse = Boolean.parseBoolean(element.getAttribute("blockResponse"));

		builder.addPropertyValue("key", new ProxyRuleKey(port, ip));
		
		parseChildren(element, parserContext, builder);
		
		if (name != null)
			builder.addPropertyValue("name", name);
		if (blockRequest)
			builder.addPropertyValue("blockRequest", blockRequest);
		if (blockResponse)
			builder.addPropertyValue("blockResponse", blockResponse);
	}
	
}
