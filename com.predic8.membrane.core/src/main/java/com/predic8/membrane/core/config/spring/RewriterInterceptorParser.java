package com.predic8.membrane.core.config.spring;

import java.util.*;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Mapping;

public class RewriterInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return RewriteInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "rewriter");
		builder.addPropertyValue("mappings",getMappings(element));
	}

	private List<Mapping> getMappings(Element e) {
		List<Mapping> m = new ArrayList<Mapping>();
		for (Element mapping : DomUtils.getChildElementsByTagName(e, "map")) {
			m.add( new Mapping(mapping.getAttribute("from"), mapping.getAttribute("to"), mapping.getAttribute("do")));
		}
		return m;
	}
}
