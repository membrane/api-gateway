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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.RuleManager.*;
import com.predic8.membrane.core.config.spring.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.jmx.*;
import com.predic8.membrane.core.kubernetes.*;
import com.predic8.membrane.core.kubernetes.client.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.context.*;
import org.springframework.context.support.*;

import javax.annotation.concurrent.*;
import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.*;
import java.util.concurrent.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.jmx.JmxExporter.*;

/**
 * @description <p>
 *              Membrane Service Proxy's main object.
 *              </p>
 *              <p>
 *              The router is a Spring Lifecycle object: It is automatically started and stopped according to the
 *              Lifecycle of the Spring Context containing it. In Membrane's standard setup (standalone or in a J2EE web
 *              app), Membrane itself controls the creation of the Spring Context and its Lifecycle.
 *              </p>
 *              <p>
 *              In this case, the router is <i>hot deployable</i>: It can monitor <i>proxies.xml</i>, the Spring
 *              configuration file, for changes and reinitialize the Spring Context, when a change is detected. Note
 *              that, during the Spring Context restart, the router object itself along with almost all other Membrane
 *              objects (interceptors, etc.) will be recreated.
 *              </p>
 * @topic 1. Membrane Service Proxy
 */
@MCMain(
		outputPackage="com.predic8.membrane.core.config.spring",
		outputName="router-conf.xsd",
		targetNamespace="http://membrane-soa.org/proxies/1/")
@MCElement(name="router")
public class Router implements Lifecycle, ApplicationContextAware, BeanNameAware {

	private static final Logger log = LoggerFactory.getLogger(Router.class.getName());

	/**
	 * In case more than one <router hotDeploy="true" /> starts within the same
	 * app context, we track them here, so they start only one
	 * HotDeploymentThread.
	 */
	protected static final HashSet<ApplicationContext> hotDeployingContexts = new HashSet<>();

	private ApplicationContext beanFactory;
	private String baseLocation;

	protected RuleManager ruleManager = new RuleManager();
	protected ExchangeStore exchangeStore;
	protected Transport transport;
	protected ResolverMap resolverMap;
	protected DNSCache dnsCache = new DNSCache();
	protected ExecutorService backgroundInitializator =
			Executors.newSingleThreadExecutor(new HttpServerThreadFactory("Router Background Initializator"));
	protected HotDeploymentThread hdt;
	protected URIFactory uriFactory = new URIFactory(false);
	protected Statistics statistics = new Statistics();
	protected String jmxRouterName;

	private boolean hotDeploy = true;
	private final Object lock = new Object();
	@GuardedBy("lock")
	private boolean running;

	private int retryInitInterval = 5 * 60 * 1000; // 5 minutes
	private boolean retryInit;
	private Timer reinitializator;
	private String id;
	private final KubernetesWatcher kubernetesWatcher = new KubernetesWatcher(this);
	private final TimerManager timerManager = new TimerManager();
	private final HttpClientFactory httpClientFactory = new HttpClientFactory(timerManager);
	private final KubernetesClientFactory kubernetesClientFactory = new KubernetesClientFactory(httpClientFactory);

	public Router() {
		ruleManager.setRouter(this);
		resolverMap = new ResolverMap(timerManager, httpClientFactory, kubernetesClientFactory);
		resolverMap.addRuleResolver(this);
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

	@SuppressWarnings("NullableProblems")
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		beanFactory = applicationContext;
		if (applicationContext instanceof BaseLocationApplicationContext)
			setBaseLocation(((BaseLocationApplicationContext)applicationContext).getBaseLocation());
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

	/**
	 * @description Spring Bean ID of an {@link ExchangeStore}. The exchange store will be used by this router's
	 *              components ({@link AdminConsoleInterceptor}, {@link ExchangeStoreInterceptor}, etc.) by default, if
	 *              no other exchange store is explicitly set to be used by them.
	 * @default create a {@link LimitedMemoryExchangeStore} limited to the size of 1 MB.
	 */
	@MCAttribute
	public void setExchangeStore(ExchangeStore exchangeStore) {
		this.exchangeStore = exchangeStore;
	}

	public Transport getTransport() {
		return transport;
	}

	@MCChildElement(order=1, allowForeign=true)
	public void setTransport(Transport transport) {
		this.transport = transport;
	}

	public HttpClientConfiguration getHttpClientConfig() {
		return resolverMap.getHTTPSchemaResolver().getHttpClientConfig();
	}

	@MCChildElement()
	public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
		resolverMap.getHTTPSchemaResolver().setHttpClientConfig(httpClientConfig);
	}

	public DNSCache getDnsCache() {
		return dnsCache;
	}

	public ResolverMap getResolverMap() {
		return resolverMap;
	}

	/**
	 * Closes all ports (if any were opened) and waits for running exchanges to complete.
	 * <p>
	 * When running as an embedded servlet, this has no effect.
	 */
	public void shutdown() throws IOException {
		backgroundInitializator.shutdown();
		if (transport != null)
			transport.closeAll();
		timerManager.shutdown();
	}

	public void shutdownAll() throws IOException{
		for(String s : this.getBeanFactory().getBeanNamesForType(Router.class)){
			((Router) this.getBeanFactory().getBean(s)).shutdown();
		}
	}

	/**
	 * Closes all ports (if any were opened), but does not wait for running exchanges to complete.
	 *
	 * @deprecated Simply invokes {@link #shutdown()}. "Not waiting" is not supported anymore, as open connections can
	 *             now be forcibly closed after a timeout. See
	 *             {@link HttpTransport#setForceSocketCloseOnHotDeployAfter(int)}.
	 */
	@Deprecated
	public void shutdownNoWait() throws IOException {
		shutdown();
	}

	public ExecutorService getBackgroundInitializator() {
		return backgroundInitializator;
	}

	public Rule getParentProxy(Interceptor interceptor) {
		for (Rule r : getRuleManager().getRules()) {
			if (r.getInterceptors() != null)
				for (Interceptor i : r.getInterceptors())
					if (i == interceptor)
						return r;
		}
		throw new IllegalArgumentException("No parent proxy found for the given interceptor.");
	}

	public void add(Rule rule) throws IOException {
		ruleManager.addProxyAndOpenPortIfNew(rule);
	}

	public void init() throws Exception {
		initInternalProxies();
		initRemainingRules();
		transport.init(this);
	}

	private void initRemainingRules() throws Exception {
		for (Rule rule : getRuleManager().getRules().stream().filter(r -> !(r instanceof InternalProxy)).toList())
			rule.init(this);
	}

	private void initInternalProxies() throws Exception {
		for (Rule rule : getRuleManager().getRules().stream().filter(r -> r instanceof InternalProxy).toList())
			rule.init(this);
	}

	@Override
	public void start() {
		try {
			if (transport == null && beanFactory != null && beanFactory.getBeansOfType(Transport.class).values().size() > 0)
				throw new RuntimeException("unclaimed transport detected. - please migrate to 4.0");
			if (exchangeStore == null)
				exchangeStore = new LimitedMemoryExchangeStore();
			if (transport == null)
				transport = new HttpTransport();
			kubernetesWatcher.start();

			init();
			initJmx();
			getRuleManager().openPorts();

			try {
				if (hotDeploy)
					startHotDeployment();
			} catch (Exception e) {
				shutdown();
				throw e;
			}

			if (retryInitInterval > 0)
				startAutoReinitializator();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		startJmx();

		synchronized (lock) {
			running = true;
		}
		log.info(PRODUCT_NAME + " " + VERSION + " up and running!");
	}

	private void startJmx() {
		if(getBeanFactory() != null) {
			try{
				((JmxExporter) getBeanFactory().getBean(JMX_EXPORTER_NAME)).initAfterBeansAdded();
			}catch(NoSuchBeanDefinitionException ignored){
				// If bean is not available, then don't start jmx
			}
		}
	}

	private void initJmx() {
		if (beanFactory != null) {
			try {
				JmxExporter exporter = (JmxExporter) beanFactory.getBean(JMX_EXPORTER_NAME);
				//exporter.removeBean(prefix + jmxRouterName);
				exporter.addBean("org.membrane-soa:00=routers, name=" + jmxRouterName, new JmxRouter(this, exporter));
			}catch(NoSuchBeanDefinitionException ignored){
				// If bean is not available, then dont init jmx
			}
		}
	}

	private void startHotDeployment() {
		if (hdt != null)
			throw new IllegalStateException("Hot deployment already started.");
		if (!(beanFactory instanceof TrackingApplicationContext))
			throw new RuntimeException("ApplicationContext is not a TrackingApplicationContext. Please set <router hotDeploy=\"false\">.");
		if (!(beanFactory instanceof AbstractRefreshableApplicationContext))
			throw new RuntimeException("ApplicationContext is not a AbstractRefreshableApplicationContext. Please set <router hotDeploy=\"false\">.");
		synchronized (hotDeployingContexts) {
			if (hotDeployingContexts.contains(beanFactory))
				return;
			hotDeployingContexts.add(beanFactory);
		}
		hdt = new HotDeploymentThread((AbstractRefreshableApplicationContext) beanFactory);
		hdt.setFiles(((TrackingApplicationContext) beanFactory).getFiles());
		hdt.start();
	}

	private void stopHotDeployment() {
		stopAutoReinitializator();
		if (hdt != null) {
			hdt.stopASAP();
			hdt = null;
			synchronized (hotDeployingContexts) {
				hotDeployingContexts.remove(beanFactory);
			}
		}
	}

	private void startAutoReinitializator() {
		if (getInactiveRules().isEmpty())
			return;

		reinitializator = new Timer("auto reinitializator", true);
		reinitializator.schedule(new TimerTask() {
			@Override
			public void run() {
				tryReinitialization();
			}
		}, retryInitInterval, retryInitInterval);
	}

	public void tryReinitialization() {
		boolean stillFailing = false;
		ArrayList<Rule> inactive = getInactiveRules();
		if (inactive.size() > 0) {
			log.info("Trying to activate all inactive rules.");
			for (Rule rule : inactive) {
				try {
					log.info("Trying to start API {}.",rule.getName());
					Rule newRule = rule.clone();
					if (!newRule.isActive()) {
						log.warn("New rule for API {} is still not active.", rule.getName());
						stillFailing = true;
					}
					getRuleManager().replaceRule(rule, newRule);
				} catch (CloneNotSupportedException e) {
					log.error("", e);
				}
			}
		}
		if (stillFailing)
			log.info("There are still inactive rules.");
		else {
			stopAutoReinitializator();
			log.info("All rules have been initialized.");
		}
	}

	private void stopAutoReinitializator() {
		Timer reinitializator2 = reinitializator;
		if (reinitializator2 != null) {
			reinitializator2.cancel();
		}
	}

	@Override
	public void stop() {
		try {
			kubernetesWatcher.stop();
			if (hdt != null)
				stopHotDeployment();
			shutdown();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		synchronized (lock) {
			running = false;
			lock.notifyAll();
		}
	}

	public void stopAll(){
		for(String s : this.getBeanFactory().getBeanNamesForType(Router.class)){
			((Router) this.getBeanFactory().getBean(s)).stop();
		}
	}

	@Override
	public boolean isRunning() {
		synchronized (lock) {
			return running;
		}
	}

	/**
	 * @description
	 * <p>Whether changes to the router's configuration file should automatically trigger a restart.
	 * </p>
	 * <p>
	 * Monitoring the router's configuration file <i>proxies.xml</i> is only possible, if the router
	 * is created by a Spring Application Context which supports monitoring.
	 * </p>
	 * @default true
	 * @param hotDeploy If true the hot deploy feature is activated
	 */
	@MCAttribute
	public void setHotDeploy(boolean hotDeploy) {
		if (isRunning()) {
			if (this.hotDeploy && !hotDeploy)
				stopHotDeployment();
			if (!this.hotDeploy && hotDeploy)
				startHotDeployment();
		}
		this.hotDeploy = hotDeploy;
	}

	public boolean isHotDeploy() {
		return hotDeploy;
	}

	public int getRetryInitInterval() {
		return retryInitInterval;
	}

	/**
	 * @description number of milliseconds after which reinitialization of &lt;soapProxy&gt;s should be attempted periodically
	 * @default 5 minutes
	 */
	@MCAttribute
	public void setRetryInitInterval(int retryInitInterval) {
		this.retryInitInterval = retryInitInterval;
	}

	private ArrayList<Rule> getInactiveRules() {
		ArrayList<Rule> inactive = new ArrayList<>();
		for (Rule rule : getRuleManager().getRules())
			if (!rule.isActive())
				inactive.add(rule);
		return inactive;
	}

	public String getBaseLocation() {
		return baseLocation;
	}

	public void setBaseLocation(String baseLocation) {
		this.baseLocation = baseLocation;
	}

	public ApplicationContext getBeanFactory() {
		return beanFactory;
	}

	public boolean isRetryInit() {
		return retryInit;
	}

	/**
	 * @explanation
	 * <p>Whether the router should continue startup, if initialization of a rule (proxy, serviceProxy or soapProxy) failed
	 * (for example, when a WSDL a component depends on could not be downloaded).</p>
	 * <p>If false, the router will exit with code -1 just after startup, when the initialization of a rule failed.</p>
	 * <p>If true, the router will continue startup, and all rules which could not be initialized will be <i>inactive</i> (=not
	 * {@link Rule#isActive()}).</p>
	 * <h3>Inactive rules</h3>
	 * <p>Inactive rules will simply be ignored for routing decissions for incoming requests.
	 * This means that requests for inactive rules might be routed using different routes or result in a "400 Bad Request"
	 * when no active route could be matched to the request.</p>
	 * <p>Once rules become active due to reinitialization, they are considered in future routing decissions.</p>
	 * <h3>Reinitialization</h3>
	 * <p>Inactive rules may be <i>reinitialized</i> and, if reinitialization succeeds, become active.</p>
	 * <p>By default, reinitialization is attempted at regular intervals using a timer (see {@link #setRetryInitInterval(int)}).</p>
	 * <p>Additionally, using the {@link AdminConsoleInterceptor}, an admin may trigger reinitialization of inactive rules at any time.</p>
	 * @default false
	 */
	@MCAttribute
	public void setRetryInit(boolean retryInit) {
		this.retryInit = retryInit;
	}

	public URIFactory getUriFactory() {
		return uriFactory;
	}

	@MCChildElement(order=-1, allowForeign=true)
	public void setUriFactory(URIFactory uriFactory) {
		this.uriFactory = uriFactory;
	}

	public Statistics getStatistics() {
		return statistics;
	}

	@MCAttribute
	public void setJmx(String name){
		jmxRouterName = name;
	}

	public String getJmx(){
		return jmxRouterName;
	}

	@SuppressWarnings("NullableProblems")
	@Override
	public void setBeanName(String s) {
		this.id = s;
	}

	public String getId(){
		return id;
	}

	/**
	 * waits until the router has shut down
	 */
	public void waitFor() throws InterruptedException {
		synchronized (lock) {
			while (isRunning())
				lock.wait();
		}
	}

	public TimerManager getTimerManager() {
		return timerManager;
	}

	public KubernetesClientFactory getKubernetesClientFactory() {
		return kubernetesClientFactory;
	}

	public HttpClientFactory getHttpClientFactory() {
		return httpClientFactory;
	}
}