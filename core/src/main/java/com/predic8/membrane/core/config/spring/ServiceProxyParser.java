package com.predic8.membrane.core.config.spring;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class ServiceProxyParser extends AbstractProxyParser {

	@Override
	protected Class<?> getBeanClass(Element element) {
		return ServiceProxy.class;
	}
	
	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		super.doParse(element, parserContext, builder);
		
		setIdIfNeeded(element, parserContext, "serviceProxy");

		String name = StringUtils.defaultIfEmpty(element.getAttribute("name"), null);
		int port = Integer.parseInt(StringUtils.defaultIfEmpty(element.getAttribute("port"), "80"));
		boolean blockRequest = Boolean.parseBoolean(element.getAttribute("blockRequest"));
		boolean blockResponse = Boolean.parseBoolean(element.getAttribute("blockResponse"));
		String host = StringUtils.defaultIfEmpty(element.getAttribute("host"), "*");
		String method = StringUtils.defaultIfEmpty(element.getAttribute("method"), "*");
		String ip = StringUtils.defaultIfEmpty(element.getAttribute("ip"), null);
		String path = ".*";

		builder.addPropertyValue("key", new ServiceProxyKey(host, method, path, port, ip));
		
		parseChildren(element, parserContext, builder);
		
		if (name != null)
			builder.addPropertyValue("name", name);
		if (blockRequest)
			builder.addPropertyValue("blockRequest", blockRequest);
		if (blockResponse)
			builder.addPropertyValue("blockResponse", blockResponse);
	}
	
	protected void handleChildElement(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder) {
		if (isMembraneNamespace(ele.getNamespaceURI())) {
			if (StringUtils.equals("target", ele.getLocalName())) {
				builder.addPropertyValue("targetHost", StringUtils.defaultIfEmpty(ele.getAttribute("host"), null));
				builder.addPropertyValue("targetPort", Integer.parseInt(StringUtils.defaultIfEmpty(ele.getAttribute("port"), "80")));
				builder.addPropertyValue("targetURL", 
						ele.hasAttribute("service") ?
								"service:" + ele.getAttribute("service") :
									StringUtils.defaultIfEmpty(ele.getAttribute("url"), null));
				
				NodeList nl2 = ele.getChildNodes();
				for (int i2 = 0; i2 < nl2.getLength(); i2++) {
					Node node2 = nl2.item(i2);
					if (node2 instanceof Element) {
						Element ele2 = (Element) node2;
						
						parseElementToProperty(ele2, parserContext, builder, "sslOutboundParser");
					}
				}

				return;
			}
			if (StringUtils.equals("path", ele.getLocalName())) {
				
				builder.addPropertyValue("key.usePathPattern", true);
				builder.addPropertyValue("key.pathRegExp", Boolean.parseBoolean(ele.getAttribute("isRegExp")));
				builder.addPropertyValue("key.path", ele.getTextContent());
				
				return;
			}
			if (StringUtils.equals("ssl", ele.getLocalName())) {
				parseElementToProperty(ele, parserContext, builder, "sslInboundParser");
				return;
			}
		} 

		super.handleChildElement(ele, parserContext, builder);
	}

}
