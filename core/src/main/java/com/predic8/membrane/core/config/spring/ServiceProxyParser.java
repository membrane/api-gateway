package com.predic8.membrane.core.config.spring;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class ServiceProxyParser extends AbstractParser {

	private static final String MEMBRANE_NAMESPACE = "http://membrane-soa.org/router/beans/1/";

	@Override
	protected Class<?> getBeanClass(Element element) {
		return ServiceProxy.class;
	}
	
	private int interceptorCount = 0;

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		super.doParse(element, parserContext, builder);

		String name = StringUtils.defaultIfEmpty(element.getAttribute("name"), null);
		int port = Integer.parseInt(StringUtils.defaultIfEmpty(element.getAttribute("port"), "80"));
		boolean blockRequest = Boolean.parseBoolean(element.getAttribute("blockRequest"));
		boolean blockResponse = Boolean.parseBoolean(element.getAttribute("blockResponse"));
		String host = StringUtils.defaultIfEmpty(element.getAttribute("host"), "*");
		String method = StringUtils.defaultIfEmpty(element.getAttribute("method"), "*");
		String ip = StringUtils.defaultIfEmpty(element.getAttribute("ip"), null);
		String path = ".*";

		builder.addPropertyValue("key", new ServiceProxyKey(host, method, path, port, ip));
		
		NodeList nl = element.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element) {
				Element ele = (Element) node;
				
				if (StringUtils.equals(MEMBRANE_NAMESPACE, ele.getNamespaceURI())) {
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

						continue;
					}
					if (StringUtils.equals("path", ele.getLocalName())) {
						
						builder.addPropertyValue("key.usePathPattern", true);
						builder.addPropertyValue("key.pathRegExp", Boolean.parseBoolean(ele.getAttribute("isRegExp")));
						builder.addPropertyValue("key.path", ele.getTextContent());
						
						continue;
					}
					if (StringUtils.equals("ssl", ele.getLocalName())) {
						parseElementToProperty(ele, parserContext, builder, "sslInboundParser");
						continue;
					}
				} 
				
				parseInterceptor(ele, parserContext, builder, Flow.REQUEST_RESPONSE);
			}
		}

		
		if (name != null)
			builder.addPropertyValue("name", name);
		if (blockRequest)
			builder.addPropertyValue("blockRequest", blockRequest);
		if (blockResponse)
			builder.addPropertyValue("blockResponse", blockResponse);
	}
	
	private void parseElementToProperty(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder, String property) {
		BeanDefinitionParserDelegate delegate = parserContext.getDelegate();

		if (delegate.isDefaultNamespace(ele)) {
			Object o = delegate.parsePropertySubElement(ele, builder.getBeanDefinition());
			builder.addPropertyValue(property, o);
		} else {
			BeanDefinition bd = delegate.parseCustomElement(ele);
			builder.addPropertyValue(property, bd);
		}
	}

	protected void parseInterceptor(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder, Flow flow) {
		BeanDefinitionParserDelegate delegate = parserContext.getDelegate();

		if (StringUtils.equals(MEMBRANE_NAMESPACE, ele.getNamespaceURI())) {
			boolean request = StringUtils.equals("request", ele.getLocalName());
			boolean response = StringUtils.equals("response", ele.getLocalName());
			if (request || response) {
				NodeList nl = ele.getChildNodes();
				for (int i = 0; i < nl.getLength(); i++) {
					Node node = nl.item(i);
					if (node instanceof Element) {
						Element ele2 = (Element) node;
						parseInterceptor(ele2, parserContext, builder, request ? Flow.REQUEST : Flow.RESPONSE);
					}
				}
				return;
			}
		}
		if (delegate.isDefaultNamespace(ele)) {
			Object o = delegate.parsePropertySubElement(ele, builder.getBeanDefinition());
			if (flow != Flow.REQUEST_RESPONSE) {
				if (o instanceof BeanDefinitionHolder) {
					((BeanDefinitionHolder) o).getBeanDefinition().getPropertyValues().addPropertyValue("flow", flow);
				} else if (o instanceof RuntimeBeanReference) {
					parserContext.getRegistry().getBeanDefinition(((RuntimeBeanReference) o).getBeanName())
							.getPropertyValues().addPropertyValue("flow", flow);
				} else {
					parserContext.getReaderContext().error("Don't know how to set flow on " + o.getClass(), ele);
				}
			}
			builder.addPropertyValue("interceptors[" + interceptorCount++ + "]", o);
		} else {
			BeanDefinition bd = delegate.parseCustomElement(ele);
			if (flow != Flow.REQUEST_RESPONSE)
				bd.getPropertyValues().addPropertyValue("flow", flow);
			builder.addPropertyValue("interceptors[" + interceptorCount++ + "]", bd);
		}
	}
	

}
