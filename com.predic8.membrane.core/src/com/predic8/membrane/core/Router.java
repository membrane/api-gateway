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
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.balancer.ClusterManager;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.util.DNSCache;

public class Router {

	protected static Log log = LogFactory.getLog(Router.class.getName());
	
	protected RuleManager ruleManager = new RuleManager();

	protected ExchangeStore exchangeStore = new ForgetfulExchangeStore();

	protected Transport transport;

	protected ConfigurationManager configurationManager = new ConfigurationManager();

	protected ClusterManager clusterManager;
	
	protected static Router router;

	protected static FileSystemXmlApplicationContext beanFactory;

	protected DNSCache dnsCache = new DNSCache();
	
	public Router() {
		configurationManager.setRouter(this);
		ruleManager.setRouter(this);
	}
	
	public static Router init(String configFileName) throws MalformedURLException {
		log.debug("loading spring config from classpath: " + configFileName);
		return init(configFileName, Router.class.getClassLoader());
	}
	
	public static Router init(String resource, ClassLoader classLoader) {
		log.debug("loading spring config: " + resource);
		try {
			beanFactory = new FileSystemXmlApplicationContext(   new String[] { resource }, false );
		} catch (Error e) {
			e.printStackTrace();
		}
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

	public void setConfigurationManager(ConfigurationManager configurationManager) {
		this.configurationManager = configurationManager;
		configurationManager.setRouter(this);
	}
	
	public ClusterManager getClusterManager() {
		return clusterManager;
	}

	public void setClusterManager(ClusterManager clusterManager) {
		this.clusterManager = clusterManager;
	}

	public Collection<Interceptor> getInterceptors() {
		Map<String, Interceptor> map = beanFactory.getBeansOfType(Interceptor.class);
		Set<String> keys = map.keySet();
		for (String id : keys) {
			Interceptor i = map.get(id);
			i.setId(id);
			i.setRouter(this);
		}
		return map.values();
	}
	
	public Interceptor getInterceptorFor(String id) {
		Interceptor i = (Interceptor)beanFactory.getBean(id, Interceptor.class);
		i.setId(id); //very important, returned bean does not have id set	
		i.setRouter(this);
		return i;
	}

	public DNSCache getDnsCache() {
		return dnsCache;
	}
	
}
