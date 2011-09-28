package com.predic8.membrane.core.config.spring;

import org.apache.commons.logging.*;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.w3c.dom.Element;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.Global;
import com.predic8.membrane.core.interceptor.balancer.ClusterManager;

public class RouterParser extends
		AbstractSingleBeanDefinitionParser {
	
	private static Log log = LogFactory.getLog(RouterParser.class.getName());

	@SuppressWarnings("rawtypes")
	protected Class getBeanClass(Element element) {
		return Router.class;
	}

	protected void doParse(Element e, BeanDefinitionBuilder bean) {
		e.setAttribute("id", "router");
		bean.addPropertyReference("transport", "transport");

		if (e.hasAttribute("exchangeStore")) {
			bean.addPropertyReference("exchangeStore", e.getAttribute("exchangeStore"));
		}
		
		if (e.hasAttribute("useClusterManager") && Boolean.parseBoolean(e.getAttribute("useClusterManager"))) {
			log.debug("Cluster Manager added.");
			bean.addPropertyValue("clusterManager", new ClusterManager());
		}					
		
		ConfigurationManager cm = new ConfigurationManager();
		cm.setHotDeploy(Boolean.parseBoolean(e.getAttribute("hotDeploy")));
		cm.getProxies().setAdjustHostHeader(Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_ADJ_HOST_HEADER)));
		cm.getProxies().setIndentMessage(Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_INDENT_MSG)));
		cm.getProxies().setAdjustContentLength(Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_ADJ_CONTENT_LENGTH)));
		cm.getProxies().setTrackExchange(Boolean.parseBoolean(e.getAttribute(Global.ATTRIBUTE_AUTO_TRACK)));
		bean.addPropertyValue("configurationManager", cm);
	}
}