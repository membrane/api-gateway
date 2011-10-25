package com.predic8.membrane.core.config.spring;

import java.util.*;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.*;
import org.w3c.dom.Node;

import com.predic8.membrane.core.interceptor.balancer.*;

public class BalancerInterceptorParser extends AbstractParser {

	protected Class getBeanClass(Element element) {
		return LoadBalancingInterceptor.class;
	}

	@Override
	protected void doParse(Element e, BeanDefinitionBuilder builder) {
		setIdIfNeeded(e, "balancer");

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

	private void parserEndpoints(BeanDefinitionBuilder b, Element e) {
		List<String> m = new ArrayList<String>();
		for (Element node : DomUtils.getChildElementsByTagName(e, "node")) {
			m.add(node.getAttribute("host") + ":" + node.getAttribute("port"));
		}
		b.addPropertyValue("endpoints", m);
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
