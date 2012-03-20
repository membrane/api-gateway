package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.predic8.membrane.core.interceptor.balancer.Balancer;
import com.predic8.membrane.core.interceptor.balancer.ByThreadStrategy;
import com.predic8.membrane.core.interceptor.balancer.JSESSIONIDExtractor;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.RoundRobinStrategy;
import com.predic8.membrane.core.interceptor.balancer.XMLElementSessionIdExtractor;

public class BalancerInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return LoadBalancingInterceptor.class;
	}

	@Override
	protected void doParse(Element e, BeanDefinitionBuilder builder) {
		setIdIfNeeded(e, "balancer");
		
		if (e.hasAttribute("name")) 
			builder.addPropertyValue("name", e.getAttribute("name"));
		else
			builder.addPropertyValue("name", Balancer.DEFAULT_NAME);
		
		if (e.hasAttribute("sessionTimeout"))
			builder.addPropertyValue("sessionTimeout", e.getAttribute("sessionTimeout"));

		parseChildren(builder, e);
	}

	private void parseChildren(BeanDefinitionBuilder b, Element e) {

		NodeList nL = e.getChildNodes();

		for (int i = 0; i < nL.getLength(); i++) {
			Node n = nL.item(i);
			if (n.getNodeType() != Node.ELEMENT_NODE)
				continue;

			if ("xmlSessionIdExtractor".equals(n.getNodeName())) {
				parserSessionIdExtractor(b, (Element) n);
			} else if ("jSessionIdExtractor".equals(n.getNodeName())) {
				JSESSIONIDExtractor ext = new JSESSIONIDExtractor();
				b.addPropertyValue("sessionIdExtractor", ext);
			} else if ("byThreadStrategy".equals(n.getNodeName())) {
				parserByThreadStrategy(b, (Element) n);
			} else if ("roundRobinStrategy".equals(n.getNodeName())) {
				parserRoundRobinStrategy(b, (Element) n);
			}
		}
	}

	private void parserSessionIdExtractor(BeanDefinitionBuilder b, Element e) {
		XMLElementSessionIdExtractor ext = new XMLElementSessionIdExtractor();
		ext.setLocalName(e.getAttribute("localName"));
		ext.setNamespace(e.getAttribute("namespace"));
		b.addPropertyValue("sessionIdExtractor", ext);
	}

	private void parserByThreadStrategy(BeanDefinitionBuilder b, Element e) {
		ByThreadStrategy strat = new ByThreadStrategy();
		strat.setMaxNumberOfThreadsPerEndpoint(Integer.parseInt(e
				.getAttribute("maxNumberOfThreadsPerEndpoint")));
		strat.setRetryTimeOnBusy(Integer.parseInt(e
				.getAttribute("retryTimeOnBusy")));
		b.addPropertyValue("dispatchingStrategy", strat);
	}

	private void parserRoundRobinStrategy(BeanDefinitionBuilder b, Element e) {
		b.addPropertyValue("dispatchingStrategy", new RoundRobinStrategy());
	}

}
