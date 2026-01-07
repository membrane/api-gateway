/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.beanregistry.*;
import com.predic8.membrane.core.config.spring.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.kubernetes.client.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http.streampump.*;
import com.predic8.membrane.core.util.*;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.stream.Streams;
import org.jetbrains.annotations.NotNull;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.context.*;

import javax.annotation.concurrent.GuardedBy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class DefaultMainComponents implements MainComponents {

    private static final Logger log = LoggerFactory.getLogger(DefaultMainComponents.class);

    private final DefaultRouter router;

    private ApplicationContext beanFactory;

    protected BeanRegistry registry;

    protected Transport transport;

    @GuardedBy("this")
    boolean beansDefined = false;

    public DefaultMainComponents(DefaultRouter router) {
        log.debug("Creating new router.");
        this.router = router;
    }

    /**
     * Registers fallbackBeanDefinitions with the registry for all classes annotated with @Resource .
     *
     * In case a class has multiple constructors, the one with the highest Priority annotation is used.
     *
     * The constructor's parameters are resolved from the registry.
     */
    public synchronized void defineResourceBeans() {
        if (beansDefined)
            return;
        beansDefined = true;

        loadResourceClasses().forEach(clazzName -> {
            try {
                Class<?> aClass = getClass().getClassLoader().loadClass(clazzName);
                Resource resource = aClass.getAnnotation(Resource.class);
                //noinspection unchecked
                registry.registerFallbackIfAbsent((Class<Object>) (resource.type().equals(Object.class) ? aClass : resource.type()), () -> createResource(aClass));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private List<String> loadResourceClasses() {
        try (InputStream is = requireNonNull(getClass().getResourceAsStream("/com/predic8/membrane/core/config/spring/resources.txt"))) {
            return new BufferedReader(new InputStreamReader(is, UTF_8)).lines().toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Object createResource(Class<?> aClass) {
        try {
            Constructor<?> constructor = chooseConstructor(aClass.getConstructors());
            return constructor.newInstance(fillParameterList(constructor));
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private Object @NotNull [] fillParameterList(Constructor<?> constructor) {
        Object[] parameters = new Object[constructor.getParameterCount()];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = registry.getBean(constructor.getParameterTypes()[i]).orElseThrow();
        }
        return parameters;
    }

    private Constructor<?> chooseConstructor(Constructor<?>[] constructors) {
        if (constructors.length == 1)
            return constructors[0];
        constructors = Streams.of(constructors).filter(c -> c.isAnnotationPresent(Priority.class)).toArray(Constructor<?>[]::new);
        if (constructors.length == 0)
            throw new RuntimeException("No constructor annotated with @Priority found.");
        Arrays.sort(constructors, Comparator.comparingInt(c -> c.getAnnotation(Priority.class).value()));
        return constructors[0];
    }

    public void init() {
        log.debug("Initializing.");

        if (registry == null) {
            registry = new BeanRegistryImplementation(null);
            registry.register("router", router);
        }

        defineResourceBeans();

        if (transport == null)
            transport = registry.getBean(Transport.class).orElseThrow();
        transport.init(router);
    }

    public void setRules(Collection<Proxy> proxies) {
        getRuleManager().removeAllRules();
        for (Proxy proxy : proxies)
            getRuleManager().addProxy(proxy, RuleManager.RuleDefinitionSource.SPRING);
    }

    @Override
    public RuleManager getRuleManager() {
        return getRegistry().getBean(RuleManager.class).orElseThrow();
    }

    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        beanFactory = ctx;
        if (ctx instanceof BaseLocationApplicationContext blac)
            router.getConfiguration().setBaseLocation(blac.getBaseLocation());
    }

    public void setRuleManager(RuleManager ruleManager) {
        log.debug("Setting ruleManager.");
        ruleManager.setRouter(router);
        getRegistry().register("ruleManager", ruleManager);
    }

    @Override
    public ExchangeStore getExchangeStore() {
        return getRegistry().getBean(ExchangeStore.class).orElseThrow();
    }

    public void setExchangeStore(ExchangeStore exchangeStore) {
        getRegistry().register("exchangeStore", exchangeStore);
    }

    @Override
    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public HttpClientConfiguration getHttpClientConfig() {
        return getResolverMap().getHTTPSchemaResolver().getHttpClientConfig();
    }

    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        getResolverMap().getHTTPSchemaResolver().setHttpClientConfig(httpClientConfig);
    }

    @Override
    public DNSCache getDnsCache() {
        return getRegistry().getBean(DNSCache.class).orElseThrow();
    }

    @Override
    public ResolverMap getResolverMap() {
        return getRegistry().getBean(ResolverMap.class).orElseThrow();
    }

    @Override
    public Statistics getStatistics() {
        return getRegistry().getBean(Statistics.class).orElseThrow();
    }

    public void setGlobalInterceptor(GlobalInterceptor globalInterceptor) {
        getRegistry().register("globalInterceptor", globalInterceptor);
    }

    @Override
    public TimerManager getTimerManager() {
        return registry.getBean(TimerManager.class).orElseThrow();
    }

    @Override
    public KubernetesClientFactory getKubernetesClientFactory() {
        return getRegistry().getBean(KubernetesClientFactory.class).orElseThrow();
    }

    public HttpClientFactory getHttpClientFactory() {
        return getRegistry().getBean(HttpClientFactory.class).orElseThrow();
    }

    public FlowController getFlowController() {
        return getRegistry().getBean(FlowController.class).orElseThrow();
    }

    public void setRegistry(BeanRegistry registry) {
        this.registry = registry;
        defineResourceBeans();
    }

    public BeanRegistry getRegistry() {
        if (registry == null) {
            registry = new BeanRegistryImplementation(null);
            registry.register("router", router);
        }
        return registry;
    }

    @Override
    public ApplicationContext getBeanFactory() {
        return beanFactory;
    }
}
