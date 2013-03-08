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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCMain;
import com.predic8.membrane.core.RuleManager.RuleDefinitionSource;
import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.http.HttpServerThreadFactory;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.util.DNSCache;
import com.predic8.membrane.core.util.ResourceResolver;

@MCMain(
		outputPackage="com.predic8.membrane.core.config.spring",
		outputName="router-conf.xsd",
		targetNamespace="http://membrane-soa.org/proxies/1/")
@MCElement(name="router", group="basic")
public class Router implements Lifecycle, ApplicationContextAware {

	private static final Log log = LogFactory.getLog(Router.class.getName());

	private ApplicationContext beanFactory;

	protected RuleManager ruleManager = new RuleManager();
	protected ExchangeStore exchangeStore;
	protected Transport transport;
	protected ConfigurationManager configurationManager = new ConfigurationManager(this);
	protected ResourceResolver resourceResolver = new ResourceResolver();
	protected DNSCache dnsCache = new DNSCache();
	protected ExecutorService backgroundInitializator = 
			Executors.newSingleThreadExecutor(new HttpServerThreadFactory("Router Background Initializator"));
	
	private boolean running;

	public Router() {
		ruleManager.setRouter(this);
	}

	public Collection<Rule> getRules() {
		return getRuleManager().getRulesBySource(RuleDefinitionSource.SPRING);
	}
	
	@MCChildElement(order=3)
	public void setRules(Collection<Rule> proxies) {
		for (Rule rule : proxies)
			getRuleManager().addProxy(rule, RuleDefinitionSource.SPRING);
	}

	public static Router init(String configFileName)
			throws MalformedURLException {
		log.debug("loading spring config from classpath: " + configFileName);
		return init(configFileName, Router.class.getClassLoader());
	}

	public static Router init(String resource, ClassLoader classLoader) {
		log.debug("loading spring config: " + resource);

		TrackingFileSystemXmlApplicationContext beanFactory = 
				new TrackingFileSystemXmlApplicationContext(new String[] { resource }, false);
		beanFactory.setClassLoader(classLoader);
		beanFactory.refresh();
		
		beanFactory.start();

		return (Router) beanFactory.getBean("router");
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		beanFactory = applicationContext;
		configurationManager.setApplicationContext(applicationContext); // hack until ConfigurationManager lifecycle is managed by Spring
	}
	
	public void setConfigurationManager(ConfigurationManager configurationManager) {
		boolean hotDeploy = this.configurationManager.isHotDeploy();
		this.configurationManager = configurationManager;
		configurationManager.setApplicationContext(beanFactory);
		configurationManager.setHotDeploy(hotDeploy);
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

	@MCAttribute
	public void setExchangeStore(ExchangeStore exchangeStore) {
		this.exchangeStore = exchangeStore;
	}

	public Transport getTransport() {
		return transport;
	}

	@MCChildElement(order=1)
	public void setTransport(Transport transport) {
		this.transport = transport;
	}

	public ConfigurationManager getConfigurationManager() {
		return configurationManager;
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

	/**
	 * Closes all ports (if any were opened) and waits for running exchanges to complete.
	 * 
	 * When running as an embedded servlet, this has no effect.
	 */
	public void shutdown() throws IOException {
		backgroundInitializator.shutdown();
		getTransport().closeAll();
	}
	
	/**
	 * Closes all ports (if any were opened), but does not wait for running exchanges to complete.
	 */
	public void shutdownNoWait() throws IOException {
		getTransport().closeAll(false);
	}
	
	public ExecutorService getBackgroundInitializator() {
		return backgroundInitializator;
	}
	
	public Rule getParentProxy(Interceptor interceptor) {
		for (Rule r : getRuleManager().getRules()) {
			for (Interceptor i : r.getInterceptors())
				if (i == interceptor)
					return r;
		}
		throw new IllegalArgumentException("No parent proxy found for the given interceptor.");
	}

	public void init() throws Exception {
		for (Rule rule : getRuleManager().getRules())
			rule.init(this);
		transport.init(this);
	}

	@Override
	public void start() {
		try {
			if (beanFactory.getBeansOfType(Rule.class).values().size() > 0)
				throw new RuntimeException("unclaimed rule detected. - please migrate to 4.0");
			if (transport == null && beanFactory.getBeansOfType(Transport.class).values().size() > 0)
				throw new RuntimeException("unclaimed transport detected. - please migrate to 4.0");
			if (exchangeStore == null)
				exchangeStore = new LimitedMemoryExchangeStore();
			if (transport == null)
				transport = new HttpTransport();
			init();
			getRuleManager().openPorts();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		running = true;
	}

	@Override
	public void stop() {
		try {
			configurationManager.stopHotDeployment();
			shutdown();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}
	
	@MCAttribute
	public void setHotDeploy(boolean hotDeploy) {
		getConfigurationManager().setHotDeploy(hotDeploy);
	}
	
	public boolean isHotDeploy() {
		return getConfigurationManager().isHotDeploy();
	}
}
