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

package com.predic8.membrane.core.router;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.annot.beanregistry.*;
import com.predic8.membrane.core.cli.*;
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
import com.predic8.membrane.core.proxies.RuleManager.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.router.hotdeploy.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http.streampump.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.context.*;
import org.springframework.context.support.*;

import javax.annotation.concurrent.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.proxies.RuleManager.RuleDefinitionSource.*;
import static com.predic8.membrane.core.util.DLPUtil.*;

/*
 * Responsibilities:
 * - Start and stop Membrane, proxies and internal services
 * - Control the lifecycle of proxies
 *
 * Questions:
 * - What if a proxy needs to make a call to another proxy during init(Tests: e.g. B2C)
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
public class DefaultRouter extends AbstractRouter implements ApplicationContextAware, BeanRegistryAware, BeanNameAware, BeanCacheObserver {

    private static final Logger log = LoggerFactory.getLogger(DefaultRouter.class);

    protected DefaultMainComponents mainComponents;

    private String id;

    private Configuration configuration = new Configuration();

    private final Object lock = new Object();

    @GuardedBy("lock")
    private boolean running;

    @GuardedBy("lock")
    private boolean initialized;

    /**
     * HotDeployer for changes on the configuration file.
     * Not synchronized, since only modified during initialization
     */
    private HotDeployer hotDeployer = new DefaultHotDeployer();

    private RuleReinitializer reinitializer;

    public DefaultRouter() {
        log.debug("Creating new router.");
        mainComponents = new DefaultMainComponents(this);
    }

    /**
     * Initializes the {@code Router}
     * - by setting up its associated components.
     * - calling init() on each of its {@link Proxy} instances
     *
     * This method ensures that the {@code Router} and its dependencies are prepared for operation.
     * But it does not start the router itself. Use {@link #start()} to start the router.
     * If start() is called a separate call to init() is not needed.
     */
    public void init() {
        log.debug("Initializing.");

        // TODO: Temporary guard, to check correct behaviour, remove later
        synchronized (lock) {
            if (initialized)
                throw new IllegalStateException("Router already initialized.");

            mainComponents.init();

            initProxies();

            initialized = true;
            reinitializer = new RuleReinitializer(this); // Bean
        }
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

        synchronized (lock) {
            if (!initialized)
                init();
        }

        try {
            getRegistry().getBean(KubernetesWatcher.class).ifPresent(KubernetesWatcher::start);
            JmxExporter.start(this);
            getRuleManager().openPorts();
            hotDeployer.start(this);
            if (configuration.getRetryInitInterval() > 0)
                reinitializer.start();
        } catch (DuplicatePathException e) {
            handleDuplicateOpenAPIPaths(e);
        } catch (OpenAPIParsingException e) {
            handleOpenAPIParsingException(e);
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

    @MCChildElement(order = 3)
    public void setRules(Collection<Proxy> proxies) {
        getRuleManager().removeAllRules();
        for (Proxy proxy : proxies)
            getRuleManager().addProxy(proxy, RuleDefinitionSource.SPRING);
    }

    public Collection<Proxy> getRules() {
        return getRuleManager().getRules();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        mainComponents.setApplicationContext(applicationContext);
    }

    public RuleManager getRuleManager() {
        return mainComponents.getRuleManager();
    }

    public void setRuleManager(RuleManager ruleManager) {
        mainComponents.setRuleManager(ruleManager);
    }

    public ExchangeStore getExchangeStore() {
        return mainComponents.getExchangeStore();
    }

    /**
     * @description Spring Bean ID of an {@link ExchangeStore}. The exchange store will be used by this router's
     * components ({@link AdminConsoleInterceptor}, {@link ExchangeStoreInterceptor}, etc.) by default, if
     * no other exchange store is explicitly set to be used by them.
     * @default create a {@link LimitedMemoryExchangeStore} limited to the size of 1 MB.
     */
    @MCAttribute
    public void setExchangeStore(ExchangeStore exchangeStore) {
        mainComponents.setExchangeStore(exchangeStore);
    }

    @Override
    public Transport getTransport() {
        return mainComponents.getTransport();
    }

    /**
     * @description Used to override the default 'transport' chain. The transport chain is the one global
     * interceptor chain called *for every* incoming HTTP Exchanges. The transport chain uses &lt;userFeature/&gt;
     * to call 'down' to a specific &lt;api/&gt;, &lt;serviceProxy/&gt; or similar. The default transport chain
     * is shown in proxies-full-sample.xml .
     */
    @MCChildElement(order = 1, allowForeign = true)
    public void setTransport(Transport transport) {
        mainComponents.setTransport(transport);
    }

    public HttpClientConfiguration getHttpClientConfig() {
        return mainComponents.getHttpClientConfig();
    }

    /**
     * @description A 'global' (per router) &lt;httpClientConfig&gt;. This instance is used everywhere
     * a HTTP Client is used. In every specific place, you should still be able to configure a local
     * &lt;httpClientConfig&gt; (with higher precedence compared to this global instance).
     */
    @MCChildElement()
    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        mainComponents.setHttpClientConfig(httpClientConfig);
    }

    public DNSCache getDnsCache() {
        return mainComponents.getDnsCache();
    }

    public ResolverMap getResolverMap() {
        return mainComponents.getResolverMap();
    }

    /**
     * Adds a proxy to the router.
     * Can be called at any time before or after start().
     *
     * If called after start(), the port will be opened automatically.
     *
     * @param proxy
     * @throws IOException
     */
    public void add(Proxy proxy) throws IOException {
        log.debug("Adding proxy {}.", proxy.getName());
        RuleManager ruleManager = getRuleManager();

        if (running && proxy instanceof SSLableProxy sp) {
            sp.init(this);
            ruleManager.addProxyAndOpenPortIfNew(sp);
        } else {
            ruleManager.addProxy(proxy, MANUAL);
        }

    }

    @Override
    public void stop() {
        getRegistry().getBean(KubernetesWatcher.class).ifPresent(KubernetesWatcher::stop);
        hotDeployer.stop();

         if (mainComponents.getTransport() != null)
            mainComponents.getTransport().closeAll();
        mainComponents.getTimerManager().shutdown();

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

    public ApplicationContext getBeanFactory() {
        return mainComponents.getBeanFactory();
    }

    public Statistics getStatistics() {
        return mainComponents.getStatistics();
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
        mainComponents.setGlobalInterceptor(globalInterceptor);
    }

    public String getId() {
        return id;
    }

    /**
     * waits until the router has shut down
     */
    public void waitFor() {
        synchronized (lock) {
            while (running) {
                try {
                    lock.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    @Override
    public TimerManager getTimerManager() {
        return  mainComponents.getTimerManager();
    }

    @Override
    public KubernetesClientFactory getKubernetesClientFactory() {
        return mainComponents.getKubernetesClientFactory();
    }

    @Override
    public HttpClientFactory getHttpClientFactory() {
        return mainComponents.getHttpClientFactory();
    }

    @Override
    public FlowController getFlowController() {
        return mainComponents.getFlowController();
    }

    @Override
    public void handleAsynchronousInitializationResult(boolean success) {
        log.debug("Asynchronous initialization finished.");
        if (!success && !configuration.isRetryInit())
            System.exit(1);
        ApiInfo.logInfosAboutStartedProxies(getRuleManager());
    }

    @Override
    public void handleBeanEvent(BeanDefinitionChanged bdc, Object bean, Object oldBean) throws IOException {
        log.debug("Bean changed: type={} instance={}", bean.getClass().getSimpleName(), bean);
        if (!(bean instanceof Proxy newProxy)) {
            // should not happen, as handleBeanEvent() is only called for beans passing isActivatable().
            throw new IllegalArgumentException("Bean must be a Proxy instance, but got: " + bean.getClass().getName());
        }

        if (newProxy.getName() == null)
            newProxy.setName(bdc.bd().getName());

        if (bdc.action().isAdded()) {
            add(newProxy);
        } else if (bdc.action().isDeleted())
            getRuleManager().removeRule((Proxy) oldBean);
        else if (bdc.action().isModified()) {
            getRuleManager().replaceRule((Proxy) oldBean, newProxy);
        }
    }

    @Override
    public boolean isActivatable(BeanContainer bc) {
        return bc.produces(Proxy.class);
    }

    public AbstractRefreshableApplicationContext getRef() {
        if (mainComponents.getBeanFactory() instanceof AbstractRefreshableApplicationContext bf)
            return bf;
        throw new RuntimeException("ApplicationContext is not a AbstractRefreshableApplicationContext. Please set <router hotDeploy=\"false\">.");
    }

    @Override
    public void setRegistry(BeanRegistry registry) {
        mainComponents.setRegistry(registry);
    }

    @Override
    public BeanRegistry getRegistry() {
       return mainComponents.getRegistry();
    }

    public void applyConfiguration(Configuration configuration) {
        hotDeployer = configuration.isHotDeploy() ? new DefaultHotDeployer() : new NullHotDeployer();
        this.configuration = configuration;
    }

    /**
     * Sets the configuration object for this router.
     * Only used for xml
     *
     * @param configuration the configuration object
     */
    @MCChildElement(order = -1)
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    public RuleReinitializer getReinitializer() {
        return reinitializer;
    }

    private static void handleOpenAPIParsingException(OpenAPIParsingException e) {
        System.err.printf("""
                ================================================================================================
                
                Configuration Error: Could not read or parse OpenAPI Document
                
                Reason: %s
                
                Location: %s
                
                Have a look at the proxies.xml file.
                """, e.getMessage(), e.getLocation());
        throw new ExitException();
    }

    private static void handleDuplicateOpenAPIPaths(DuplicatePathException e) {
        System.err.printf("""
                ================================================================================================
                
                Configuration Error: Several OpenAPI Documents share the same path!
                
                An API routes and validates requests according to the path of the OpenAPI's servers.url fields.
                Within one API the same path should be used only by one OpenAPI. Change the paths or place
                openapi-elements into separate api-elements.
                
                Shared path: %s
                %n""", e.getPath());
        throw new ExitException();
    }
}