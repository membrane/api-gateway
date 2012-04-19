/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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
import java.util.*;

import org.apache.commons.logging.*;
import org.springframework.context.support.*;

import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.util.*;

public class Router {

	private static final Log log = LogFactory.getLog(Router.class.getName());

	static Router router;
	static AbstractApplicationContext beanFactory;

	protected RuleManager ruleManager = new RuleManager();
	protected ExchangeStore exchangeStore = new ForgetfulExchangeStore();
	protected Transport transport;
	protected final ConfigurationManager configurationManager = new ConfigurationManager(this);
	protected ResourceResolver resourceResolver = new ResourceResolver();
	protected DNSCache dnsCache = new DNSCache();

	public Router() {
		ruleManager.setRouter(this);
	}

	public static Router init(String configFileName)
			throws MalformedURLException {
		log.debug("loading spring config from classpath: " + configFileName);
		return init(configFileName, Router.class.getClassLoader());
	}

	public static Router init(String resource, ClassLoader classLoader) {
		log.debug("loading spring config: " + resource);

		beanFactory = new FileSystemXmlApplicationContext(
				new String[] { resource }, false);
		beanFactory.setClassLoader(classLoader);
		beanFactory.refresh();

		router = (Router) beanFactory.getBean("router");
		return router;
	}

	public static Router getInstance() {
		return router;
	}

	public RuleManager getRuleManager() {
		return ruleManager;
	}

	public void setRuleManager(RuleManager ruleManager) {
		this.ruleManager = ruleManager;
		ruleManager.setRouter(this);
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

	public Collection<Interceptor> getInterceptors() {
		Map<String, Interceptor> map = beanFactory.getBeansOfType(Interceptor.class);
		for (Map.Entry<String, Interceptor> entry : map.entrySet()) {
			entry.getValue().setId(entry.getKey());
			entry.getValue().setRouter(this);
		}
		return map.values();
	}

	public Interceptor getInterceptorFor(String id) {
		Interceptor i = beanFactory.getBean(id, Interceptor.class);
		i.setId(id); // very important, returned bean does not have id set
		i.setRouter(this);
		return i;
	}

	public <E> E getBean(String id, Class<E> clazz) {
		return beanFactory.getBean(id, clazz);
	}

	public DNSCache getDnsCache() {
		return dnsCache;
	}

	public ResourceResolver getResourceResolver() {
		return resourceResolver;
	}

	public void setResourceResolver(ResourceResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}

	public static AbstractApplicationContext getBeanFactory() {
		return beanFactory;
	}

	public static void setBeanFactory(AbstractApplicationContext beanFactory) {
		Router.beanFactory = beanFactory;
	}
	
}
