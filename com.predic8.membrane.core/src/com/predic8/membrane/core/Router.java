package com.predic8.membrane.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.transport.Transport;

public class Router {

	protected RuleManager ruleManager;

	protected ExchangeStore exchangeStore;

	protected Transport transport;

	protected ConfigurationManager configurationManager;

	protected static Router router;

	protected static XmlBeanFactory beanFactory;

	protected static Log log = LogFactory.getLog(Router.class.getName());

	public static void init(String configFileName) {
		log.debug("loading spring config: " + configFileName);
		init(new ClassPathResource(configFileName));
	}

	public static void init(Resource resource) {
		log.debug("loading spring config: " + resource);
		beanFactory = new XmlBeanFactory(resource);
		router = (Router) beanFactory.getBean("router");
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

}
