package com.predic8.membrane.core.config.spring;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import com.predic8.membrane.core.rules.SOAPProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class SOAPProxyParser extends AbstractProxyParser {
	@Override
	protected Class<?> getBeanClass(Element element) {
		return SOAPProxy.class;
	}
	
	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		super.doParse(element, parserContext, builder);
		
		setIdIfNeeded(element, parserContext, "soapProxy");

		String name = StringUtils.defaultIfEmpty(element.getAttribute("name"), null);
		int port = Integer.parseInt(StringUtils.defaultIfEmpty(element.getAttribute("port"), "80"));
		boolean blockRequest = Boolean.parseBoolean(element.getAttribute("blockRequest"));
		boolean blockResponse = Boolean.parseBoolean(element.getAttribute("blockResponse"));
		String host = StringUtils.defaultIfEmpty(element.getAttribute("host"), "*");
		String ip = StringUtils.defaultIfEmpty(element.getAttribute("ip"), null);
		String path = ".*";
		// no @method
		String wsdl = element.getAttribute("wsdl");
		String portName = StringUtils.defaultIfEmpty(element.getAttribute("portName"), null);

		builder.addPropertyValue("key", new ServiceProxyKey(host, "*", path, port, ip));
		
		parseChildren(element, parserContext, builder);
		
		if (name != null)
			builder.addPropertyValue("name", name);
		if (blockRequest)
			builder.addPropertyValue("blockRequest", blockRequest);
		if (blockResponse)
			builder.addPropertyValue("blockResponse", blockResponse);
		builder.addPropertyValue("wsdl", wsdl);
		if (portName != null)
			builder.addPropertyValue("portName", portName);
	}
	
	protected void handleChildElement(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder) {
		if (isMembraneNamespace(ele.getNamespaceURI())) {
			if (StringUtils.equals("path", ele.getLocalName())) {
				
				builder.addPropertyValue("key.usePathPattern", true);
				builder.addPropertyValue("key.pathRegExp", false /*Boolean.parseBoolean(ele.getAttribute("isRegExp"))*/ ); // true not allowed by SOAPProxy
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
