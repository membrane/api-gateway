package com.predic8.membrane.core;

import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.transport.Transport;

public class Router {

	protected RuleManager ruleManager;

	protected ExchangeStore exchangeStore;

	protected Transport transport;

	protected ConfigurationManager configurationManager;

	protected static Router router;

	protected static XmlBeanFactory beanFactory;

	protected static Log log = LogFactory.getLog(Router.class.getName());

	
	public static Router init(String configFileName) throws MalformedURLException {
		log.debug("loading spring config from classpath: " + configFileName);
		return init(new ClassPathResource(configFileName));
		
	}

	public static Router init(Resource resource) {
		log.debug("loading spring config: " + resource);
		beanFactory = new XmlBeanFactory(resource);
		return router = (Router) beanFactory.getBean("router");
	}
	
	public static Router getInstance() {
		return router;
	}

	public RuleManager getRuleManager() {
		return ruleManager;
	}

	public void setRuleManager(RuleManager ruleManager) {
		this.ruleManager = ruleManager;
	}

	public ExchangeStore getExchangeStore() {
		return exchangeStore;
	}

	public void setExchangeStore(ExchangeStore exchangeStore) {
		this.exchangeStore = exchangeStore;
	}

	public Transport getTransport() {
		return transport;
	}

	public void setTransport(Transport transport) {
		this.transport = transport;
	}

	public ConfigurationManager getConfigurationManager() {
		return configurationManager;
	}

	public void setConfigurationManager(ConfigurationManager configurationManager) {
		this.configurationManager = configurationManager;
	}
	
	public Collection<Interceptor> getInterceptors() {
		Map<String, Interceptor> map = beanFactory.getBeansOfType(Interceptor.class);
		Set<String> keys = map.keySet();
		for (String id : keys) {
			map.get(id).setId(id);
		}
		return map.values();
	}
	
	public Interceptor getInterceptorFor(String id) {
		Interceptor interceptor = (Interceptor)beanFactory.getBean(id, Interceptor.class);
		return interceptor;
	}

}
