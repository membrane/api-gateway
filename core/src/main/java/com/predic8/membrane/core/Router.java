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

import com.jayway.jsonpath.internal.function.sequence.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.annot.beanregistry.*;
import com.predic8.membrane.core.RuleManager.*;
import com.predic8.membrane.core.config.spring.*;
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

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.MANUAL;
import static com.predic8.membrane.core.jmx.JmxExporter.*;
import static com.predic8.membrane.core.util.DLPUtil.*;
import static java.util.concurrent.Executors.*;

/*
 TODO:
 - ADR: First start router than init and add proxies or first init proxies and add them later?

 ADR:
 - The Router is responsible for the lifecycle of the proxies
 - Ports are opened in start()
 - init()
   - does not open ports
   - inits the proxies
     - In sequence as added or reverse or not defined?
 - new Router(), add(proxy) init() start()
 - add(proxy) could be called any time
   - If router is started port will be opened if needed by the proxy
 - What if a proxy needs to make a call to another proxy during init(Tests: e.g. B2C)
 - Delete addProxyAndOpenPortIfNew() from RuleManager

 HTTPRouter:
 - Purpose? Test?

 */

/**
 * @description <p>
 * Membrane API Gateway's main object.
 * </p>
 * <p>
 * The router is a Spring Lifecycle object: It is automatically started and stopped according to the
 * Lifecycle of the Spring Context containing it. In Membrane's standard setup
 * Membrane itself controls the creation of the Spring Context and its Lifecycle.
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

    protected BeanRegistry registry;

    //
    // Configuration
    //
    private String id;
    private String baseLocation;

    private Configuration config = new Configuration();

    //
    // Components
    //
    protected Transport transport;

    private final TimerManager timerManager = new TimerManager();
    private final HttpClientFactory httpClientFactory = new HttpClientFactory(timerManager);
    private final KubernetesClientFactory kubernetesClientFactory = new KubernetesClientFactory(httpClientFactory);
    protected final ResolverMap resolverMap;

    protected final ExecutorService backgroundInitializer =
            newSingleThreadExecutor(new HttpServerThreadFactory("Router Background Initializer"));


    protected final Statistics statistics = new Statistics();

    private final Object lock = new Object();

    @GuardedBy("lock")
    private boolean running;

    private boolean initialized;

    //
    // Reinitialization
    //
    private Timer reinitializer;

    /**
     * HotDeployer for changes on the XML configuration file. Does not cover YAML.
     * Not synchronized, since only modified during initialization
     * Initialized with NullHotDeployer to avoid NPEs
     */
    private HotDeployer hotDeployer = new DefaultHotDeployer();

    public Router() {
        log.debug("Creating new router.");
        resolverMap = new ResolverMap(httpClientFactory, kubernetesClientFactory);
        resolverMap.addRuleResolver(this);
    }

    //
    // Initialization
    //

    /**
     * Initializes the {@code Router}
     * - by setting up its associated components.
     * - calling init() on each of its {@link Proxy} instances.
     * <p>
     * This method ensures that the {@code Router} and its dependencies are prepared for operation.
     * But it does not start the router itself. Use {@link #start()} to start the router.
     * If start() is called a separate call to init() is not needed.
     * The init() is useful for testing without the expensive start() call.
     */
    public void init() {
        log.debug("Initializing.");

        // TODO: Temporary guard, to check correct behaviour, remove later
        if (initialized)
            throw new IllegalStateException("Router already initialized.");

        getRegistry().registerIfAbsent(ExchangeStore.class, LimitedMemoryExchangeStore::new);
        getRegistry().registerIfAbsent(RuleManager.class, () -> {
            RuleManager rm = new RuleManager();
            rm.setRouter(this);
            return rm;
        });

        // Transport last
        if (transport == null) {
            transport = new HttpTransport();
        }
        transport.init(this);

        initProxies();

        initialized = true;
    }

    private void initProxies() {
        log.debug("Initializing proxies.");
        for (Proxy proxy : getRuleManager().getRules()) {
            log.debug("Initializing proxy {}.", proxy.getName());
            proxy.init(this);
        }
    }

    /**
     * Initializes a {@link Router} instance from the specified Spring XML configuration resource.
     *
     * @param resource the path to the Spring XML configuration file that defines the {@link Router} bean
     * @return the initialized {@link Router} instance
     * @throws RuntimeException if no {@link Router} bean is found or more than one {@link Router} bean is found
     */
    public static Router initByXML(String resource) {
        log.debug("loading spring config: {}", resource);

        TrackingFileSystemXmlApplicationContext bf =
                new TrackingFileSystemXmlApplicationContext(new String[]{resource}, false);
        bf.refresh();

        if (bf.getBeansOfType(Router.class).size() > 1) {
            throw new RuntimeException(
                    "More than one router bean found in the Spring configuration (%s). This is no longer supported."
                            .formatted(Arrays.toString(bf.getBeanDefinitionNames()))
            );
        }
        Router router = bf.getBean("router", Router.class);
        router.init();
        bf.start(); // Starting ApplicationContext will also call router.start(). Init should happen before.
        return router;
    }

    public static Router initFromXMLString(String xmlString) {
        log.debug("Loading spring config from string");
        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
        ctx.load(new ByteArrayResource(xmlString.getBytes(StandardCharsets.UTF_8)));
        ctx.refresh();
        ctx.start();
        return ctx.getBean(Router.class);
    }

    /**
     * Starts the main processing logic of the application.
     * This method initializes essential components, validates the configuration,
     * and starts background services required for the application's functionality.
     * Key responsibilities:
     * - Initializes the application if it hasn't been initialized yet.
     * - Opens TCP ports
     */
    @Override
    public void start() {
        log.debug("Starting.");
        displayTraceWarning();

        if (!initialized)
            init();

        try {

            getRegistry().getBean(KubernetesWatcher.class).ifPresent(KubernetesWatcher::start);

            initJmx();
            startJmx();
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
            log.error("Could not start router.", e);
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        }

        synchronized (lock) {
            running = true;
        }
    }

    public Collection<Proxy> getRules() {
        log.debug("Getting rules.");
        return getRuleManager().getRulesBySource(RuleDefinitionSource.SPRING); // TODO: Source?
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
        var rm = getRegistry().getBean(RuleManager.class);
        if (rm.isPresent()) return rm.get();
        RuleManager rmi = new RuleManager();
        rmi.setRouter(this);
        getRegistry().register("ruleManager", rmi);
        return rmi;
    }

    public void setRuleManager(RuleManager ruleManager) {
        log.debug("Setting ruleManager.");
        ruleManager.setRouter(this);
        getRegistry().register("ruleManager", ruleManager);
    }

    public ExchangeStore getExchangeStore() {
        return registry.getBean(ExchangeStore.class).orElseThrow();
    }

    /**
     * @description Spring Bean ID of an {@link ExchangeStore}. The exchange store will be used by this router's
     * components ({@link AdminConsoleInterceptor}, {@link ExchangeStoreInterceptor}, etc.) by default, if
     * no other exchange store is explicitly set to be used by them.
     * @default create a {@link LimitedMemoryExchangeStore} limited to the size of 1 MB.
     */
    @MCAttribute
    public void setExchangeStore(ExchangeStore exchangeStore) {
        getRegistry().register("exchangeStore", exchangeStore);
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
        return getRegistry().registerIfAbsent(DNSCache.class, DNSCache::new);
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

    /**
     * Adds a proxy to the router and initializes it.
     * @param proxy
     * @throws IOException
     */
    public void add(Proxy proxy) throws IOException {
        log.debug("Adding proxy {}.", proxy.getName());
        RuleManager ruleManager = getRuleManager();
        if (proxy instanceof SSLableProxy sp) {
            if (running) { // TODO
                ruleManager.addProxyAndOpenPortIfNew(sp, MANUAL);
            }  else
                ruleManager.addProxy(sp, MANUAL);
        } else {
            ruleManager.addProxy(proxy, MANUAL);
        }
        if (running) {
            // init() has already been called
            proxy.init(this);
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
        getRegistry().getBean(KubernetesWatcher.class).ifPresent(KubernetesWatcher::stop);
        hotDeployer.stop();
        shutdown();

        synchronized (lock) {
            running = false;
            lock.notifyAll();
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
        getRegistry().register("globalInterceptor", globalInterceptor);
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
        return getRegistry().registerIfAbsent(FlowController.class, () -> new FlowController(this));
    }

    public void handleAsynchronousInitializationResult(boolean success) {
        log.debug("Asynchronous initialization finished.");
        if (!success && !config.isRetryInit())
            System.exit(1);
        ApiInfo.logInfosAboutStartedProxies(getRuleManager());
    }

    @Override
    public void handleBeanEvent(BeanDefinitionChanged bdc, Object bean, Object oldBean) throws IOException {
        log.debug("Bean changed: type={} instance={}", bean.getClass().getSimpleName(),bean);
        if (bean instanceof GlobalInterceptor) {
            return;
        }

        if (!(bean instanceof Proxy newProxy)) {
            throw new IllegalArgumentException("Bean must be a Proxy instance, but got: " + bean.getClass().getName());
        }

        if (newProxy.getName() == null)
            newProxy.setName(bdc.bd().getName());

        // TODO: Comment or code Should be deleted before merge
        // Comment is kept for discussion only.
        //
        // init() in Proxies was called twice, here and in Router.initRemainingRules
        // We should only keep one place. Which one is up to discussion
        //
        //        try {
        //            newProxy.init(this);
        //        } catch (ConfigurationException e) {
        //            SpringConfigurationErrorHandler.handleRootCause(e, log);
        //            throw e;
        //        } catch (Exception e) {
        //            throw new RuntimeException("Could not init rule.", e);
        //        }

        if (bdc.action().isAdded()) {
            add(newProxy);
        } else if (bdc.action().isDeleted())
            getRuleManager().removeRule((Proxy) oldBean);
        else if (bdc.action().isModified()) {
            getRuleManager().replaceRule((Proxy) oldBean, newProxy);
        }
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
        if (registry == null)
            registry = new BeanRegistryImplementation(null, this, null);
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