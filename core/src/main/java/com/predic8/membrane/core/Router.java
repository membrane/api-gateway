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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;
import org.springframework.context.support.AbstractApplicationContext;

import com.predic8.membrane.annot.MCMain;
import com.predic8.membrane.core.RuleManager.RuleDefinitionSource;
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
				"					<xsd:attribute name=\"indentMessage\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"adjustContentLength\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"trackExchange\" default=\"false\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"hotDeploy\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"				</xsd:extension> \r\n" + 
				"			</xsd:complexContent>\r\n" + 
				"		</xsd:complexType>\r\n" + 
				"	</xsd:element>\r\n" + 
				"\r\n" + 
				"	<xsd:group name=\"InterceptorGroup\">\r\n" + 
				"		<xsd:choice>\r\n" + 
				"			${interceptorReferences}\r\n" +
				"			<xsd:any namespace=\"##other\" processContents=\"strict\" />" +
				"		</xsd:choice>\r\n" + 
				"	</xsd:group>\r\n" + 
				"	\r\n" + 
				"\r\n" + 
				"	<xsd:element name=\"servletTransport\">\r\n" + 
				"		<xsd:complexType>\r\n" + 
				"			<xsd:complexContent>\r\n" + 
				"				<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"					<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"						<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"					</xsd:sequence>\r\n" + 
				"					<xsd:attribute name=\"httpClientRetries\" default=\"5\" type=\"xsd:int\" />\r\n" + 
				"					<xsd:attribute name=\"printStackTrace\" default=\"false\" type=\"xsd:boolean\" />\r\n" + 
				"					<xsd:attribute name=\"removeContextRoot\" default=\"true\" type=\"xsd:boolean\" />\r\n" + 
				"				</xsd:extension>\r\n" + 
				"			</xsd:complexContent>\r\n" + 
				"		</xsd:complexType>\r\n" + 
				"	</xsd:element>\r\n" + 
				"\r\n" +
				"${declarations}\r\n" +
				"\r\n" +
				"${raw}\r\n" +
				"	\r\n" + 
				"	<xsd:complexType name=\"EmptyElementType\">\r\n" + 
				"		<xsd:complexContent>\r\n" + 
				"			<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"				<xsd:sequence />\r\n" + 
				"			</xsd:extension>\r\n" + 
				"		</xsd:complexContent>\r\n" + 
				"	</xsd:complexType>\r\n" + 
				"	\r\n" + 
				"			<xsd:element name=\"serviceProxy\">\r\n" + 
				"				<xsd:complexType>\r\n" + 
				"					<xsd:complexContent> \r\n" + 
				"						<xsd:extension base=\"beans:identifiedType\"> \r\n" + 
				"							<xsd:sequence>\r\n" + 
				"								<xsd:element name=\"path\" minOccurs=\"0\" maxOccurs=\"1\">\r\n" + 
				"									<xsd:complexType>\r\n" + 
				"										<xsd:simpleContent>\r\n" + 
				"											<xsd:extension base=\"xsd:string\">\r\n" + 
				"												<xsd:attribute name=\"isRegExp\" type=\"xsd:boolean\"\r\n" + 
				"													use=\"optional\" />\r\n" + 
				"											</xsd:extension>\r\n" + 
				"										</xsd:simpleContent>\r\n" + 
				"									</xsd:complexType>\r\n" + 
				"								</xsd:element>\r\n" + 
				"								<xsd:element name=\"ssl\" minOccurs=\"0\" maxOccurs=\"1\" />\r\n" + 
				"								<xsd:choice minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"									<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"									<xsd:element name=\"request\">\r\n" + 
				"										<xsd:complexType>\r\n" + 
				"											<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"												<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"											</xsd:sequence>\r\n" + 
				"										</xsd:complexType>\r\n" + 
				"									</xsd:element>\r\n" + 
				"									<xsd:element name=\"response\">\r\n" + 
				"										<xsd:complexType>\r\n" + 
				"											<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"												<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"											</xsd:sequence>\r\n" + 
				"										</xsd:complexType>\r\n" + 
				"									</xsd:element>\r\n" + 
				"								</xsd:choice>\r\n" + 
				"								<xsd:element name=\"target\" minOccurs=\"0\">\r\n" + 
				"									<xsd:complexType>\r\n" + 
				"										<xsd:sequence>\r\n" + 
				"											<xsd:element name=\"ssl\" minOccurs=\"0\" maxOccurs=\"1\" />\r\n" + 
				"										</xsd:sequence>\r\n" + 
				"										<xsd:attribute name=\"host\" type=\"xsd:string\" />\r\n" + 
				"										<xsd:attribute name=\"port\" type=\"xsd:int\" />\r\n" + 
				"										<xsd:attribute name=\"url\" type=\"xsd:string\" />\r\n" + 
				"									</xsd:complexType>\r\n" + 
				"								</xsd:element>\r\n" + 
				"							</xsd:sequence>\r\n" + 
				"							<xsd:attribute name=\"name\" type=\"xsd:string\" />\r\n" + 
				"							<xsd:attribute name=\"port\" type=\"xsd:int\" />\r\n" + 
				"							<xsd:attribute name=\"blockResponse\" type=\"xsd:boolean\" />\r\n" + 
				"							<xsd:attribute name=\"blockRequest\" type=\"xsd:boolean\" />\r\n" + 
				"							<xsd:attribute name=\"host\" type=\"xsd:string\" />\r\n" + 
				"							<xsd:attribute name=\"method\" type=\"xsd:string\" />\r\n" + 
				"							<xsd:attribute name=\"ip\" type=\"xsd:string\" />\r\n" + 
				"						</xsd:extension>\r\n" + 
				"					</xsd:complexContent>\r\n" + 
				"				</xsd:complexType>\r\n" + 
				"			</xsd:element>\r\n" + 
				"			<xsd:element name=\"soapProxy\">\r\n" + 
				"				<xsd:complexType>\r\n" + 
				"					<xsd:complexContent> \r\n" + 
				"						<xsd:extension base=\"beans:identifiedType\"> \r\n" + 
				"							<xsd:sequence>\r\n" + 
				"								<xsd:element name=\"path\" minOccurs=\"0\" maxOccurs=\"1\" type=\"xsd:string\" />\r\n" + 
				"								<xsd:element name=\"ssl\" minOccurs=\"0\" maxOccurs=\"1\" />\r\n" + 
				"								<xsd:choice minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"									<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"									<xsd:element name=\"request\">\r\n" + 
				"										<xsd:complexType>\r\n" + 
				"											<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"												<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"											</xsd:sequence>\r\n" + 
				"										</xsd:complexType>\r\n" + 
				"									</xsd:element>\r\n" + 
				"									<xsd:element name=\"response\">\r\n" + 
				"										<xsd:complexType>\r\n" + 
				"											<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"												<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"											</xsd:sequence>\r\n" + 
				"										</xsd:complexType>\r\n" + 
				"									</xsd:element>\r\n" + 
				"								</xsd:choice>\r\n" + 
				"							</xsd:sequence>\r\n" + 
				"							<xsd:attribute name=\"name\" type=\"xsd:string\" />\r\n" + 
				"							<xsd:attribute name=\"port\" type=\"xsd:int\" />\r\n" + 
				"							<xsd:attribute name=\"blockResponse\" type=\"xsd:boolean\" />\r\n" + 
				"							<xsd:attribute name=\"blockRequest\" type=\"xsd:boolean\" />\r\n" + 
				"							<xsd:attribute name=\"host\" type=\"xsd:string\" />\r\n" + 
				"							<xsd:attribute name=\"wsdl\" type=\"xsd:string\" use=\"required\" />\r\n" + 
				"							<xsd:attribute name=\"portName\" type=\"xsd:string\" use=\"optional\" />\r\n" + 
				"							<xsd:attribute name=\"ip\" type=\"xsd:string\" use=\"optional\" />\r\n" + 
				"						</xsd:extension>\r\n" + 
				"					</xsd:complexContent>\r\n" + 
				"				</xsd:complexType>\r\n" + 
				"			</xsd:element>\r\n" + 
				"			<xsd:element name=\"proxy\">\r\n" + 
				"				<xsd:complexType>\r\n" + 
				"					<xsd:complexContent>\r\n" + 
				"						<xsd:extension base=\"beans:identifiedType\">\r\n" + 
				"							<xsd:sequence>\r\n" + 
				"								<xsd:choice minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"									<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"									<xsd:element name=\"request\">\r\n" + 
				"										<xsd:complexType>\r\n" + 
				"											<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"												<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"											</xsd:sequence>\r\n" + 
				"										</xsd:complexType>\r\n" + 
				"									</xsd:element>\r\n" + 
				"									<xsd:element name=\"response\">\r\n" + 
				"										<xsd:complexType>\r\n" + 
				"											<xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\r\n" + 
				"												<xsd:group ref=\"InterceptorGroup\" />\r\n" + 
				"											</xsd:sequence>\r\n" + 
				"										</xsd:complexType>\r\n" + 
				"									</xsd:element>\r\n" + 
				"								</xsd:choice>\r\n" + 
				"							</xsd:sequence>\r\n" + 
				"							<xsd:attribute name=\"name\" type=\"xsd:string\" />\r\n" + 
				"							<xsd:attribute name=\"port\" type=\"xsd:int\" />\r\n" + 
				"							<xsd:attribute name=\"ip\" type=\"xsd:string\" use=\"optional\" />\r\n" + 
				"							<xsd:attribute name=\"blockResponse\" type=\"xsd:boolean\" />\r\n" + 
				"							<xsd:attribute name=\"blockRequest\" type=\"xsd:boolean\" />\r\n" + 
				"						</xsd:extension>\r\n" + 
				"					</xsd:complexContent>\r\n" + 
				"				</xsd:complexType>\r\n" + 
				"			</xsd:element>\r\n" + 
				"" +
				"" +
				"</xsd:schema>")
public class Router implements Lifecycle {

	private static final Log log = LogFactory.getLog(Router.class.getName());

	// TODO: make non-static
	static AbstractApplicationContext beanFactory;

	protected RuleManager ruleManager = new RuleManager();
	protected ExchangeStore exchangeStore = new ForgetfulExchangeStore();
	protected Transport transport;
	protected final ConfigurationManager configurationManager = new ConfigurationManager(this);
	protected ResourceResolver resourceResolver = new ResourceResolver();
	protected DNSCache dnsCache = new DNSCache();
	protected ExecutorService backgroundInitializator = 
			Executors.newSingleThreadExecutor(new HttpServerThreadFactory("Router Background Initializator"));
	
	private boolean running;
	private boolean autowired;

	public Router() {
		ruleManager.setRouter(this);
	}
	
	@Autowired(required=false)
	public void setServiceProxies(Collection<Rule> proxies) {
		autowired = true;
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

		beanFactory = new TrackingFileSystemXmlApplicationContext(new String[] { resource }, false);
		beanFactory.setClassLoader(classLoader);
		beanFactory.refresh();
		
		beanFactory.start();

		return (Router) beanFactory.getBean("router");
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

	@Override
	public void start() {
		try {
			if (!autowired)
				setServiceProxies(beanFactory.getBeansOfType(Rule.class).values());
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
}
