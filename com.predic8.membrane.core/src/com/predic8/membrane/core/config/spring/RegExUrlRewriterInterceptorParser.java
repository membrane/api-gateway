package com.predic8.membrane.core.config.spring;

import java.util.*;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.rewrite.RegExURLRewriteInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RegExURLRewriteInterceptor.Mapping;

public class RegExUrlRewriterInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return RegExURLRewriteInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "regExUrlRewriter");
		builder.addPropertyValue("mappings",getMappings(element));
	}

	private List<Mapping> getMappings(Element e) {
		List<Mapping> m = new ArrayList<Mapping>();
		for (Element mapping : DomUtils.getChildElementsByTagName(e, "mapping")) {
			m.add( new Mapping(mapping.getAttribute("regex"), mapping.getAttribute("uri")));
		}
		return m;
	}
}
