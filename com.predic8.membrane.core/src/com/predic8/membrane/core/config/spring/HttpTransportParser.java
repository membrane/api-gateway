package com.predic8.membrane.core.config.spring;

import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.*;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.RuleMatchingInterceptor;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class HttpTransportParser extends
		AbstractSingleBeanDefinitionParser {

	protected Class getBeanClass(Element element) {
		return HttpTransport.class;
	}

	protected void doParse(Element e, BeanDefinitionBuilder bean) {
		e.setAttribute("id", "transport");

		bean.addPropertyValue("coreThreadPoolSize", e.getAttribute("coreThreadPoolSize"));
		bean.addPropertyValue("socketTimeout", e.getAttribute("socketTimeout"));
		bean.addPropertyValue("httpClientRetries", e.getAttribute("httpClientRetries"));
		bean.addPropertyValue("tcpNoDelay", e.getAttribute("tcpNoDelay"));
		bean.addPropertyValue("autoContinue100Expected", e.getAttribute("autoContinue100Expected"));
		
		bean.addPropertyValue("interceptors", parseInterceptors(e));
	}

	private List<BeanDefinition> parseInterceptors(Element e) {
		
		List<BeanDefinition> l = new ManagedList<BeanDefinition>();
		NodeList nL = e.getChildNodes();

		for (int i = 0; i < nL.getLength(); i++) {
			Node n = nL.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				l.add(getInterceptorParser((Element) n).parse((Element) n));
			}
		}
		return l;
	}

	private AbstractParser getInterceptorParser(Element e)  {
		try {
			return (AbstractParser) Class.forName("com.predic8.membrane.core.config.spring."+StringUtils.capitalize(e.getLocalName())+"InterceptorParser").newInstance();
		} catch (Exception e1) {
			throw new RuntimeException(e1);			
		}
	}
}