package com.predic8.membrane.core.config.spring;

import java.util.*;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.cbr.*;

public class CbrInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return XPathCBRInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "cbr");
		builder.addPropertyValue("routeProvider",getRouteProvider(element));
	}

	private RouteProvider getRouteProvider(Element e) {
		DefaultRouteProvider p = new DefaultRouteProvider();
		p.setRoutes(getRoutes(e));
		return p;
	}

	private List<Route> getRoutes(Element e) {
		List<Route> m = new ArrayList<Route>();
		for (Element mapping : DomUtils.getChildElementsByTagName(e, "route")) {
			m.add( new Route(mapping.getAttribute("xPath"), mapping.getAttribute("url"))); 
		}
		return m;
	}
}
