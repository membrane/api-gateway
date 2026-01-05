/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.beanregistry;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.annot.bean.*;
import com.predic8.membrane.annot.yaml.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.annotation.concurrent.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * TODO:
 * - More Tests
 * - Document
 * - For TB Unclear: Lifecycle activation/resolve
 * - oldbean
 *   - a.) Revert to second list (singletonBeans)
 *   - b.) Give proxies an unique id
 * <p>
 * For K8S UUID and name is needed cause name is only unique within a namespace.
 *
 */
public class BeanRegistryImplementation implements BeanRegistry, BeanCollector {

    private static final Logger log = LoggerFactory.getLogger(BeanRegistryImplementation.class);

    private final BeanCacheObserver observer;
    private final Grammar grammar;

    // uid -> bean
    private final ConcurrentHashMap<String, Object> singletonBeans = new ConcurrentHashMap<>(); // Order is here not critical

    // uid -> bean container
    private final Map<String, BeanContainer> bcs = new ConcurrentHashMap<>(); // Order is not critical. Order is determined by uidsToActivate

    @GuardedBy("uidsToActivate")
    private final Set<UidAction> uidsToActivate = Collections.synchronizedSet(new LinkedHashSet<>()); // keeps order

    /**
     * Protects the initialization of beans, which are unique per class.
     */
    private final Object uniqueClassInitialization = new Object();

    private final LinkedHashSet<PreDestroyCallback> preDestroyCallbacks = new LinkedHashSet<>();

    record UidAction(String uid, WatchAction action) {
    }

    record PreDestroyCallback(Object bean, Method method) {
    }

    public BeanRegistryImplementation(BeanCacheObserver observer, BeanRegistryAware registryAware, Grammar grammar) {
        this.observer = observer;
        this.grammar = grammar;
        registryAware.setRegistry(this);
    }

    @Override
    public void start() {
    }

    @Override
    public void handle(ChangeEvent changeEvent, boolean isLast) {
        if (changeEvent instanceof StaticConfigurationLoaded) {
            activationRun();
            observer.handleAsynchronousInitializationResult(uidsToActivate.isEmpty());
        }
        if (changeEvent instanceof BeanDefinitionChanged(WatchAction action, BeanDefinition bd)) {
            // Keep the latest BeanDefinition for all actions so activationRun
            // can see both metadata and the action (including DELETED).
            bcs.put(bd.getUid(), new BeanContainer(bd));

            if (!bd.isComponent() && observer.isActivatable(bd)) {
                uidsToActivate.add(new UidAction(bd.getUid(), action));
            }
            if (isLast)
                activationRun();
        }
    }

    private void activationRun() {
        Set<UidAction> uidsToRemove = new HashSet<>();
        for (UidAction uidAction : uidsToActivate) {
            BeanContainer bc = bcs.get(uidAction.uid);
            try {
                Object bean = bc.getOrCreate(this, grammar);

                // e.g., inform router about a new ApiProxy or GlobalInterceptor
                observer.handleBeanEvent(new BeanDefinitionChanged(uidAction.action, bc.getDefinition()), bean, getOldBean(uidAction.action, bc.getDefinition()));

                if (uidAction.action.isAdded() || uidAction.action.isModified())
                    singletonBeans.put(bc.getDefinition().getUid(), bean);
                if (uidAction.action.isDeleted()) {
                    singletonBeans.remove(bc.getDefinition().getUid());
                    bcs.remove(bc.getDefinition().getUid());
                }
                uidsToRemove.add(uidAction);
            } catch (Exception e) {
                log.error("Could not handle {} {}/{}", uidAction.action,
                        bc.getDefinition().getNamespace(), bc.getDefinition().getName(), e);
                throw new RuntimeException(e);
            }
        }
        for (UidAction uidAction : uidsToRemove)
            uidsToActivate.remove(uidAction);
    }

    private @Nullable Object getOldBean(WatchAction action, BeanDefinition bd) {
        Object oldBean = null;
        if (action.isModified() || action.isDeleted())
            oldBean = singletonBeans.get(bd.getUid());
        return oldBean;
    }

    @Override
    public Object resolve(String url) {
        return getFirstByName(url).orElseThrow(() -> new RuntimeException("Reference %s not found".formatted(url))).getOrCreate(this, grammar);
    }

    private @NotNull Optional<BeanContainer> getFirstByName(String url) {
        return bcs.values().stream().filter(bd -> url.equals(bd.getDefinition().getName())).findFirst();
    }

    @Override
    public List<Object> getBeans() {
        return bcs.values().stream().filter(bd -> !bd.getDefinition().isComponent())
                .map(bc -> bc.getOrCreate(this, grammar))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Grammar getGrammar() {
        return grammar;
    }

    @Override
    public <T> List<T> getBeans(Class<T> clazz) {
        return bcs.values().stream()
                .map(bc -> bc.getOrCreate(this, grammar))
                .filter(Objects::nonNull)
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .toList();
    }

    public <T> Optional<T> getBean(Class<T> clazz) {
        var beans = getBeans(clazz);
        if (beans.size() > 1) {
            var msg = "One bean was asked. But found %d beans of %s".formatted(beans.size(), clazz);
            log.error(msg);
            throw new RuntimeException(msg);
        }
        return beans.size() == 1 ? Optional.of(beans.getFirst()) : Optional.empty();
    }

    public void register(String beanName, Object bean) {
        if (bean == null)
            throw new IllegalArgumentException("bean must not be null");

        var uuid = UUID.randomUUID().toString();
        bcs.put(uuid,
                new BeanContainer(
                    new BeanDefinition(
                            "component",
                            computeBeanName(beanName, uuid),
                            null,
                            uuid,
                            null),
                    bean));
        singletonBeans.put(uuid, bean);
        // the return value of 'put' is ignored, since bean registration with
        // random keys should not yield duplicates anyway.
    }

    public <T> T registerIfAbsent(Class<T> type, Supplier<T> supplier) {
        synchronized (uniqueClassInitialization) {
            return getBean(type).orElseGet(() -> getBean(type).orElseGet(() -> {
                T created = supplier.get();
                register(null, created);
                return created;
            }));
        }
    }

    private static @NotNull String computeBeanName(String beanName, String uuid) {
        return beanName != null ? beanName : "#" + uuid;
    }

    /**
     * Registers a @PreDestroy callback for the given bean.
     */
    public void addPreDestroyCallback(Object bean, Method method) {
        preDestroyCallbacks.add(new PreDestroyCallback(bean, method));
    }

    public void close() {
        preDestroyCallbacks.reversed().forEach(pc -> {
            try {
                pc.method.invoke(pc.bean);
            } catch (Exception e) {
                log.error("Could not invoke preDestroy method of {}: {}", pc.bean, e.getMessage());
            }
        });
    }

}
