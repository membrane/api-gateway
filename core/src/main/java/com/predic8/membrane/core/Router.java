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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.AbstractApplicationContext;

import com.predic8.membrane.annot.MCMain;
import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.http.HttpServerThreadFactory;
import com.predic8.membrane.core.util.DNSCache;
import com.predic8.membrane.core.util.ResourceResolver;

@MCMain(
		outputPackage="com.predic8.membrane.core.config.spring",
		outputName="router-conf.xsd",
		xsd="" +
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
				"<xsd:schema xmlns=\"http://membrane-soa.org/router/beans/1/\"\r\n" + 
				"	xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:beans=\"http://www.springframework.org/schema/beans\"\r\n" + 
				"	targetNamespace=\"http://membrane-soa.org/router/beans/1/\"\r\n" + 
				"	elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">\r\n" + 
				"\r\n" + 
				"	<xsd:import namespace=\"http://www.springframework.org/schema/beans\" />\r\n" + 
				"\r\n" + 
				"	<xsd:element name=\"router\">\r\n" + 
				"		<xsd:complexType>\r\n" + 
				"			<xsd:complexContent>\r\n" + 
				"				<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"					<xsd:sequence />\r\n" + 
				"					<xsd:attribute name=\"exchangeStore\" type=\"xsd:string\"/>\r\n" + 
				"					<xsd:attribute name=\"adjustHostHeader\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"indentMessage\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"adjustContentLength\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"trackExchange\" default=\"false\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"hotDeploy\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"				</xsd:extension> \r\n" + 
				"			</xsd:complexContent>\r\n" + 
				"		</xsd:complexType>\r\n" + 
				"	</xsd:element>\r\n" + 
				"	\r\n" + 
				"	<xsd:complexType name=\"transportType\">\r\n" + 
				"		<xsd:complexContent>\r\n" + 
				"			<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"				<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"					<xsd:choice>\r\n" + 
				"						${interceptorReferences}\r\n" +
				"					</xsd:choice>\r\n" + 
				"				</xsd:sequence>\r\n" + 
				"				<xsd:attribute name=\"httpClientRetries\" default=\"5\" type=\"xsd:int\" />\r\n" + 
				"			</xsd:extension>\r\n" + 
				"		</xsd:complexContent>\r\n" + 
				"	</xsd:complexType>\r\n" + 
				"\r\n" + 
				"	\r\n" + 
				"	<xsd:element name=\"transport\">\r\n" + 
				"		<xsd:complexType>\r\n" + 
				"			<xsd:complexContent>\r\n" + 
				"				<xsd:extension base=\"transportType\">\r\n" + 
				"					<xsd:attribute name=\"coreThreadPoolSize\" default=\"20\" type=\"xsd:int\" />\r\n" + 
				"					<xsd:attribute name=\"socketTimeout\" default=\"30000\" type=\"xsd:int\" />\r\n" + 
				"					<xsd:attribute name=\"tcpNoDelay\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"autoContinue100Expected\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"printStackTrace\" default=\"false\" type=\"xsd:boolean\" />\r\n" + 
				"				</xsd:extension>\r\n" + 
				"			</xsd:complexContent>\r\n" + 
				"		</xsd:complexType>\r\n" + 
				"	</xsd:element>\r\n" + 
				"\r\n" + 
				"	<xsd:element name=\"servletTransport\">\r\n" + 
				"		<xsd:complexType>\r\n" + 
				"			<xsd:complexContent>\r\n" + 
				"				<xsd:extension base=\"transportType\">\r\n" + 
				"					<xsd:attribute name=\"printStackTrace\" default=\"false\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"removeContextRoot\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"				</xsd:extension>\r\n" + 
				"			</xsd:complexContent>\r\n" + 
				"		</xsd:complexType>\r\n" + 
				"	</xsd:element>\r\n" + 
				"\r\n" +
				"${interceptorDeclarations}\r\n" +
				"\r\n" +
				"	<xsd:element name=\"memoryExchangeStore\" type=\"EmptyElementType\" />\r\n" + 
				"	<xsd:element name=\"forgetfulExchangeStore\" type=\"EmptyElementType\" />\r\n" + 
				"\r\n" + 
				"	<xsd:element name=\"fileExchangeStore\">\r\n" + 
				"		<xsd:complexType>\r\n" + 
				"			<xsd:complexContent>\r\n" + 
				"				<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"					<xsd:sequence />\r\n" + 
				"					<xsd:attribute name=\"raw\" type=\"xsd:boolean\" default=\"false\"/>\r\n" + 
				"					<xsd:attribute name=\"saveBodyOnly\" type=\"xsd:boolean\" default=\"false\"/>\r\n" + 
				"					<xsd:attribute name=\"dir\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
				"				</xsd:extension>\r\n" + 
				"			</xsd:complexContent>\r\n" + 
				"		</xsd:complexType>\r\n" + 
				"	</xsd:element>\r\n" + 
				"\r\n" + 
				"\r\n" + 
				"\r\n" + 
				"	\r\n" + 
				"	<xsd:complexType name=\"EmptyElementType\">\r\n" + 
				"		<xsd:complexContent>\r\n" + 
				"			<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"				<xsd:sequence />\r\n" + 
				"			</xsd:extension>\r\n" + 
				"		</xsd:complexContent>\r\n" + 
				"	</xsd:complexType>\r\n" + 
				"	\r\n" + 
				"\r\n" + 
				"\r\n" + 
				"</xsd:schema>")
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
	protected ExecutorService backgroundInitializator = 
			Executors.newSingleThreadExecutor(new HttpServerThreadFactory("Router Background Initializator"));

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

		beanFactory = new TrackingFileSystemXmlApplicationContext(new String[] { resource }, false);
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
	}

	public ConfigurationManager getConfigurationManager() {
		return configurationManager;
	}

	public Collection<Interceptor> getInterceptors() {
		Map<String, Interceptor> map = beanFactory.getBeansOfType(Interceptor.class);
		for (Map.Entry<String, Interceptor> entry : map.entrySet()) {
			entry.getValue().setId(entry.getKey());
		}
		return map.values();
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
}
