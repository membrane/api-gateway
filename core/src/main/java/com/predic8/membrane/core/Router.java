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
import com.predic8.membrane.annot.beanregistry.BeanDefinition;
import com.predic8.membrane.annot.beanregistry.BeanDefinitionChanged;
import com.predic8.membrane.annot.beanregistry.BeanRegistry;
import com.predic8.membrane.annot.beanregistry.BeanRegistryAware;
import com.predic8.membrane.annot.yaml.*;
import com.predic8.membrane.core.RuleManager.*;
import com.predic8.membrane.core.config.spring.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.jmx.*;
import com.predic8.membrane.core.kubernetes.*;
import com.predic8.membrane.core.kubernetes.client.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.router.hotdeploy.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.context.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;

import javax.annotation.concurrent.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.jmx.JmxExporter.*;
import static com.predic8.membrane.core.util.DLPUtil.*;
import static com.predic8.membrane.core.util.text.TerminalColors.*;
import static java.util.concurrent.Executors.*;

/**
 * @description <p>
 * Membrane API Gateway's main object.
 * </p>
 * <p>
 * The router is a Spring Lifecycle object: It is automatically started and stopped according to the
 * Lifecycle of the Spring Context containing it. In Membrane's standard setup (standalone or in a J2EE web
 * app), Membrane itself controls the creation of the Spring Context and its Lifecycle.
 * </p>
 * <p>
 * In this case, the router is <i>hot deployable</i>: It can monitor <i>proxies.xml</i>, the Spring
 * configuration file, for changes and reinitialize the Spring Context, when a change is detected. Note
 * that, during the Spring Context restart, the router object itself along with almost all other Membrane
 * objects (interceptors, etc.) will be recreated.
 * </p>
 * <p>Router must be a singleton with just one instance. Membrane should never have more than one router.</p>
 * @topic 1. Proxies and Flow
 */
@MCMain(
        outputPackage = "com.predic8.membrane.core.config.spring",
        outputName = "router-conf.xsd",
        targetNamespace = "http://membrane-soa.org/proxies/1/")
@MCElement(name = "router")
public class Router implements Lifecycle, ApplicationContextAware, BeanRegistryAware, BeanNameAware, BeanCacheObserver {

    private static final Logger log = LoggerFactory.getLogger(Router.class.getName());

    private ApplicationContext beanFactory;

    private BeanRegistry registry;

    //
    // Configuration
    //
    private String id;
    private String baseLocation;

    private Configuration config = new Configuration();

    //
    // Components
    //

    protected RuleManager ruleManager = new RuleManager();
    protected final FlowController flowController;
    protected ExchangeStore exchangeStore = new LimitedMemoryExchangeStore();
    protected Transport transport;
    protected GlobalInterceptor globalInterceptor = new GlobalInterceptor();
    protected final ResolverMap resolverMap;
    protected final DNSCache dnsCache = new DNSCache();
    private final KubernetesWatcher kubernetesWatcher = new KubernetesWatcher(this);
    private final TimerManager timerManager = new TimerManager();
    private final HttpClientFactory httpClientFactory = new HttpClientFactory(timerManager);
    private final KubernetesClientFactory kubernetesClientFactory = new KubernetesClientFactory(httpClientFactory);

    protected final ExecutorService backgroundInitializer =
            newSingleThreadExecutor(new HttpServerThreadFactory("Router Background Initializer"));


    protected final Statistics statistics = new Statistics();

    private final Object lock = new Object();

    @GuardedBy("lock")
    private boolean running;

    //
    // Reinitialization
    //

    private Timer reinitializer;
    private boolean asynchronousInitialization = false;

    /**
     * HotDeployer for changes on the XML configuration file. Does not cover YAML.
     * Not synchronized, since only modified during initialization
     * Initialized with NullHotDeployer to avoid NPEs
     */
    private HotDeployer hotDeployer = new DefaultHotDeployer();

    public Router() {
        ruleManager.setRouter(this);
        resolverMap = new ResolverMap(httpClientFactory, kubernetesClientFactory);
        resolverMap.addRuleResolver(this);
        flowController = new FlowController(this);
    }

    //
    // Initialization
    //

    public void init() throws Exception {
        initRemainingRules();
        transport.init(this);
        displayTraceWarning();
    }

    private void initRemainingRules() throws Exception {
        for (Proxy proxy : getRuleManager().getRules())
            proxy.init(this);
    }

    public static Router init(String resource) {
        log.debug("loading spring config: {}", resource);

        TrackingFileSystemXmlApplicationContext bf =
                new TrackingFileSystemXmlApplicationContext(new String[]{resource}, false);
        bf.refresh();
        bf.start();

        if (bf.getBeansOfType(Router.class).size() > 1) {
            throw new RuntimeException("More than one router found in spring config (beans {}). This is not supported anymore.".formatted(bf.getBeanDefinitionNames()));
        }

        return bf.getBean("router", Router.class);
    }

    public static Router initFromXMLString(String xmlString) {
        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
        ctx.load(new ByteArrayResource(xmlString.getBytes(StandardCharsets.UTF_8)));
        ctx.refresh();
        ctx.start();
        return ctx.getBean(Router.class);
    }

    @Override
    public void start() {
        System.out.println("config = " + config.isProduction());
        try {
            if (exchangeStore == null)
                exchangeStore = new LimitedMemoryExchangeStore();
            if (transport == null)
                transport = new HttpTransport();
            kubernetesWatcher.start();

            init();
            initJmx();
            getRuleManager().openPorts();

            try {
                hotDeployer.init(this);
                hotDeployer.start();
            } catch (Exception e) {
                shutdown();
                throw e;
            }

            if (config.getRetryInitInterval() > 0)
                startAutoReinitializer();
        } catch (DuplicatePathException e) {
            System.err.printf("""
                    ================================================================================================
                    
                    Configuration Error: Several OpenAPI Documents share the same path!
                    
                    An API routes and validates requests according to the path of the OpenAPI's servers.url fields.
                    Within one API the same path should be used only by one OpenAPI. Change the paths or place
                    openapi-elements into separate api-elements.
                    
                    Shared path: %s
                    %n""", e.getPath());
            throw new ExitException();
        } catch (OpenAPIParsingException e) {
            System.err.printf("""
                    ================================================================================================
                    
                    Configuration Error: Could not read or parse OpenAPI Document
                    
                    Reason: %s
                    
                    Location: %s
                    
                    Have a look at the proxies.xml file.
                    """, e.getMessage(), e.getLocation());
            throw new ExitException();
        } catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        }

        startJmx();

        synchronized (lock) {
            running = true;
        }

        ApiInfo.logInfosAboutStartedProxies(ruleManager);
        if (!asynchronousInitialization)
            logStartupMessage();
    }

    private static void logStartupMessage() {
        log.info("{}{} {} up and running!{}", BRIGHT_CYAN(), PRODUCT_NAME, VERSION, RESET());
    }

    public Collection<Proxy> getRules() {
        return getRuleManager().getRulesBySource(RuleDefinitionSource.SPRING);
    }

    @MCChildElement(order = 3)
    public void setRules(Collection<Proxy> proxies) {
        getRuleManager().removeAllRules();
        for (Proxy proxy : proxies)
            getRuleManager().addProxy(proxy, RuleDefinitionSource.SPRING);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        beanFactory = applicationContext;
        if (applicationContext instanceof BaseLocationApplicationContext)
            setBaseLocation(((BaseLocationApplicationContext) applicationContext).getBaseLocation());
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
     * components ({@link AdminConsoleInterceptor}, {@link ExchangeStoreInterceptor}, etc.) by default, if
     * no other exchange store is explicitly set to be used by them.
     * @default create a {@link LimitedMemoryExchangeStore} limited to the size of 1 MB.
     */
    @MCAttribute
    public void setExchangeStore(ExchangeStore exchangeStore) {
        this.exchangeStore = exchangeStore;
    }

    public Transport getTransport() {
        return transport;
    }

    /**
     * @description Used to override the default 'transport' chain. The transport chain is the one global
     * interceptor chain called *for every* incoming HTTP Exchanges. The transport chain uses &lt;userFeature/&gt;
     * to call 'down' to a specific &lt;api/&gt;, &lt;serviceProxy/&gt; or similar. The default transport chain
     * is shown in proxies-full-sample.xml .
     */
    @MCChildElement(order = 1, allowForeign = true)
    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public HttpClientConfiguration getHttpClientConfig() {
        return resolverMap.getHTTPSchemaResolver().getHttpClientConfig();
    }

    /**
     * @description A 'global' (per router) &lt;httpClientConfig&gt;. This instance is used everywhere
     * a HTTP Client is used. Usually, in every specific place, you can still configure a local
     * &lt;httpClientConfig&gt; (with higher precedence compared to this global instance).
     */
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
    public void shutdown() {
        backgroundInitializer.shutdown();
        if (transport != null)
            transport.closeAll();
        timerManager.shutdown();
    }

    public ExecutorService getBackgroundInitializer() {
        return backgroundInitializer;
    }

    public void add(Proxy proxy) throws IOException {
        if (proxy instanceof SSLableProxy sp) {
            ruleManager.addProxyAndOpenPortIfNew(sp);
        } else {
            ruleManager.addProxy(proxy, RuleDefinitionSource.MANUAL);
        }
    }

    private void startJmx() {
        if (getBeanFactory() == null)
            return;

        try {
            getBeanFactory().getBean(JMX_EXPORTER_NAME, JmxExporter.class).initAfterBeansAdded();
        } catch (NoSuchBeanDefinitionException ignored) {
            // If bean is not available, then don't start jmx
        }

    }

    private void initJmx() {
        if (beanFactory == null)
            return;

        try {
            JmxExporter exporter = beanFactory.getBean(JMX_EXPORTER_NAME, JmxExporter.class);
            //exporter.removeBean(prefix + jmxRouterName);
            exporter.addBean("org.membrane-soa:00=routers, name=" + config.getJmx(), new JmxRouter(this, exporter));
        } catch (NoSuchBeanDefinitionException ignored) {
            // If bean is not available do not init jmx
        }

    }

    //
    // Reinitialization
    //

    private void startAutoReinitializer() {
        if (getInactiveRules().isEmpty())
            return;

        reinitializer = new Timer("auto reinitializer", true);
        reinitializer.schedule(new TimerTask() {
            @Override
            public void run() {
                tryReinitialization();
            }
        }, config.getRetryInitInterval(), config.getRetryInitInterval());
    }

    public void tryReinitialization() {
        boolean stillFailing = false;
        ArrayList<Proxy> inactive = getInactiveRules();
        if (!inactive.isEmpty()) {
            log.info("Trying to activate all inactive rules.");
            for (Proxy proxy : inactive) {
                try {
                    log.info("Trying to start API {}.", proxy.getName());
                    Proxy newProxy = proxy.clone();
                    if (!newProxy.isActive()) {
                        log.warn("New rule for API {} is still not active.", proxy.getName());
                        stillFailing = true;
                    }
                    getRuleManager().replaceRule(proxy, newProxy);
                } catch (CloneNotSupportedException e) {
                    log.error("", e);
                }
            }
        }
        if (stillFailing)
            log.info("There are still inactive rules.");
        else {
            stopAutoReinitializer();
            log.info("All rules have been initialized.");
        }
    }

    public void stopAutoReinitializer() {
        if (reinitializer != null) {
            reinitializer.cancel();
        }
    }

    @Override
    public void stop() {
        kubernetesWatcher.stop();
        hotDeployer.stop();
        shutdown();

        synchronized (lock) {
            running = false;
            lock.notifyAll();
        }
    }

    public void stopAll() {
        for (String s : this.getBeanFactory().getBeanNamesForType(Router.class)) {
            ((Router) this.getBeanFactory().getBean(s)).stop();
        }
    }

    @Override
    public boolean isRunning() {
        synchronized (lock) {
            return running;
        }
    }

    private ArrayList<Proxy> getInactiveRules() {
        ArrayList<Proxy> inactive = new ArrayList<>();
        for (Proxy proxy : getRuleManager().getRules())
            if (!proxy.isActive())
                inactive.add(proxy);
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

    public boolean isProduction() {
        return config.isProduction();
    }

    public Statistics getStatistics() {
        return statistics;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void setBeanName(String s) {
        this.id = s;
    }

    /**
     * @description Sets a global chain that applies to all requests and responses.
     */
    @MCChildElement(order = 2)
    public void setGlobalInterceptor(GlobalInterceptor globalInterceptor) {
        this.globalInterceptor = globalInterceptor;
    }

    public String getId() {
        return id;
    }

    /**
     * waits until the router has shut down
     */
    public void waitFor() throws InterruptedException {
        synchronized (lock) {
            while (running)
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

    public FlowController getFlowController() {
        return flowController;
    }

    public GlobalInterceptor getGlobalInterceptor() {
        return globalInterceptor;
    }

    public synchronized void setAsynchronousInitialization(boolean asynchronousInitialization) {
        this.asynchronousInitialization = asynchronousInitialization;
        notifyAll();
    }

    public void handleAsynchronousInitializationResult(boolean success) {
        if (!success && !config.isRetryInit())
            System.exit(1);
        ApiInfo.logInfosAboutStartedProxies(ruleManager);
        logStartupMessage();
        setAsynchronousInitialization(false);
    }

    @Override
    public void handleBeanEvent(BeanDefinitionChanged bdc, Object bean, Object oldBean) throws IOException {
        if (!(bean instanceof Proxy newProxy)) {
            throw new IllegalArgumentException("Bean must be a Proxy instance, but got: " + bean.getClass().getName());
        }

        if (newProxy.getName() == null)
            newProxy.setName(bdc.bd().getName());

        try {
            newProxy.init(this);
        } catch (ConfigurationException e) {
            SpringConfigurationErrorHandler.handleRootCause(e, log);
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not init rule.", e);
        }

        if (bdc.action().isAdded())
            add(newProxy);
        else if (bdc.action().isDeleted())
            getRuleManager().removeRule((Proxy) oldBean);
        else if (bdc.action().isModified())
            getRuleManager().replaceRule((Proxy) oldBean, newProxy);
    }

    @Override
    public boolean isActivatable(BeanDefinition bd) {
        return Proxy.class.isAssignableFrom(new GrammarAutoGenerated().getElement(bd.getKind()));
    }

    public AbstractRefreshableApplicationContext getRef() {
        if (beanFactory instanceof AbstractRefreshableApplicationContext bf)
            return bf;
        throw new RuntimeException("ApplicationContext is not a AbstractRefreshableApplicationContext. Please set <router hotDeploy=\"false\">.");
    }

    @Override
    public void setRegistry(BeanRegistry registry) {
        this.registry = registry;
    }

    public BeanRegistry getRegistry() {
        return registry;
    }

    public void applyConfiguration(Configuration configuration) {
        hotDeployer = configuration.isHotDeploy() ? new DefaultHotDeployer() : new NullHotDeployer();
        this.config = configuration;
    }

    public URIFactory getUriFactory() {
        return config.getUriFactory();
    }

    /**
     * Sets the configuration object for this router.
     * Only used for xml
     *
     * @param config the configuration object
     */
    @MCChildElement(order = -1)
    public void setConfig(Configuration config) {
        this.config = config;
    }

    public Configuration getConfig() {
        return config;
    }

}