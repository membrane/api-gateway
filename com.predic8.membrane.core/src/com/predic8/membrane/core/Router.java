/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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

	public static Router init(String configFileName, ClassLoader loader) throws MalformedURLException {
		log.debug("loading spring config from classpath: " + configFileName);
		return init(new ClassPathResource(configFileName), loader);
	}
	
	public static Router init(Resource resource, ClassLoader classLoader) {
		log.debug("loading spring config: " + resource);
		beanFactory = new XmlBeanFactory(resource);
		beanFactory.setBeanClassLoader(classLoader);
		router = (Router) beanFactory.getBean("router");
		router.configurationManager.setRouter(router); 
		return router; 
	}
	
	public static Router init(Resource resource) {
		return init(resource, Router.class.getClassLoader());
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
		transport.setRouter(this);
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
		interceptor.setId(id); //very important, returned bean does not have id set
		return interceptor;
	}

}
