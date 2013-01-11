package com.predic8.membrane.core.config.spring;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.predic8.membrane.core.interceptor.Interceptor.Flow;

public abstract class AbstractProxyParser extends AbstractParser {

	private Flow flow = Flow.REQUEST_RESPONSE;

	protected void handleChildElement(Element ele, ParserContext parserContext, BeanDefinitionBuilder builder) {
		if (isMembraneNamespace(ele.getNamespaceURI())) {
			// parse <interceptor> element for proxies.xml backward compatibility
			if (StringUtils.equals("interceptor", ele.getLocalName())) {
				String refid = ele.getAttribute("refid");
				if (refid == null)
					throw new RuntimeException("<interceptor> must have 'refid' attribute.");
				RuntimeBeanReference ref = new RuntimeBeanReference(refid);
				//ref.setSource(parserContext.getReaderContext().extractSource(ele));
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
