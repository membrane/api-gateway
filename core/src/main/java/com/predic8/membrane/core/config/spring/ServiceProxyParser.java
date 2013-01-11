package com.predic8.membrane.core.config.spring;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class ServiceProxyParser extends AbstractParser {

	@Override
	protected Class<?> getBeanClass(Element element) {
		return ServiceProxy.class;
	}
	
	private Flow flow = Flow.REQUEST_RESPONSE;

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
			// parse <interceptor> element for proxies.xml backward compatibility
			if (StringUtils.equals("interceptor", ele.getLocalName())) {
				String refid = ele.getAttribute("refid");
				if (refid == null)
					throw new RuntimeException("<interceptor> must have 'refid' attribute.");
				RuntimeBeanNameReference ref = new RuntimeBeanNameReference(refid);
				ref.setSource(parserContext.getReaderContext().extractSource(ele));
				handleChildObject(ele, parserContext, builder, null, ref);
				return;
			}
		} 

		if (isMembraneNamespace(ele.getNamespaceURI())) {
			boolean request = StringUtils.equals("request", ele.getLocalName());
			boolean response = StringUtils.equals("response", ele.getLocalName());
			if (request || response) {
				NodeList nl = ele.getChildNodes();
				for (int i = 0; i < nl.getLength(); i++) {
					Node node = nl.item(i);
					if (node instanceof Element) {
						Element ele2 = (Element) node;
						flow = request ? Flow.REQUEST : Flow.RESPONSE;
						super.handleChildElement(ele2, parserContext, builder);
					}
				}
				return;
			}
		}

		flow = Flow.REQUEST_RESPONSE;
		super.handleChildElement(ele, parserContext, builder);
	}

	protected void handleChildObject(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder, Class<?> clazz, Object child) {
		if (flow != Flow.REQUEST_RESPONSE) {
			if (child instanceof BeanDefinitionHolder) {
				((BeanDefinitionHolder) child).getBeanDefinition().getPropertyValues().addPropertyValue("flow", flow);
			} else if (child instanceof RuntimeBeanReference) {
				parserContext.getRegistry().getBeanDefinition(((RuntimeBeanReference) child).getBeanName())
						.getPropertyValues().addPropertyValue("flow", flow);
			} else if (child instanceof BeanDefinition) {
				((BeanDefinition)child).getPropertyValues().addPropertyValue("flow", flow);
			} else {
				parserContext.getReaderContext().error("Don't know how to set flow on " + child.getClass(), ele);
			}
		}
		
		builder.addPropertyValue("interceptors[" + incrementCounter(builder, "interceptor") + "]", child);
	}

}
