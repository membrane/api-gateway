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

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * TODO:
 * - More Tests
 * - Document
 * - Do we need uuid when then name is unique?
 * - For TB Unclear: Lifecycle activation/resolve
 */
public class BeanRegistryImplementation implements BeanRegistry, BeanCollector {

    private static final Logger log = LoggerFactory.getLogger(BeanRegistryImplementation.class);

    private final BeanCacheObserver observer;
    private final Grammar grammar;

    // uid -> bean container
    private final Map<String, BeanContainer> bcs = new ConcurrentHashMap<>(); // Order is not critical. Order is determined by uidsToActivate
    private final Set<UidAction> uidsToActivate = Collections.synchronizedSet(new LinkedHashSet<>()); // keeps order

    record UidAction(String uid, WatchAction action) {
    }

    public BeanRegistryImplementation(BeanCacheObserver observer, BeanRegistryAware registryAware, Grammar grammar) {
        this.observer = observer;
        this.grammar = grammar;
        registryAware.setRegistry(this);
    }

    private Object define(BeanDefinition bd) {
        log.debug("defining bean: {}", bd.getNode());
        try {
            if ("bean".equals(bd.getKind())) {
                return new BeanFactory(this).create(bd.getNode().path("bean"));
            }
            return GenericYamlParser.readMembraneObject(bd.getKind(),
                    grammar,
                    bd.getNode(),
                    this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        // Iterate safely over clone
        for (UidAction uidAction : cloneUidActions()) {
            BeanContainer bc = bcs.get(uidAction.uid);
            if (bc == null) {
                log.warn("Skipping activation for missing uid {}", uidAction.uid);
                continue;
            }

            BeanDefinition def = bc.getDefinition();
            Object oldBean;
            Object newBean = null;

            try {
                synchronized (bc) {
                    oldBean = (uidAction.action.isModified() || uidAction.action.isDeleted()) ? bc.getSingleton() : null;
                    if (!uidAction.action.isDeleted()) {
                        newBean = define(def);
                        bc.setSingleton(newBean);
                    }
                }

                // Remove container after releasing the lock (avoid holding bc while mutating registry map)
                if (uidAction.action.isDeleted()) {
                    bcs.remove(uidAction.uid);
                }

                observer.handleBeanEvent(new BeanDefinitionChanged(uidAction.action, def), newBean, oldBean);
            } catch (Exception e) {
                log.error("Could not handle {} {}/{}", uidAction.action, def.getNamespace(), def.getName(), e);
                throw new RuntimeException(e);
            }
        }
    }

    private @NotNull List<UidAction> cloneUidActions() {
        final List<UidAction> actions;
        synchronized (uidsToActivate) {
            actions = new ArrayList<>(uidsToActivate);
            uidsToActivate.clear();
        }
        return actions;
    }

    @Override
    public Object resolve(String url) {
        BeanContainer bc = getFirstByName(url).orElseThrow(() -> new RuntimeException("Reference %s not found".formatted(url)));

        boolean prototype = isPrototypeScope(bc.getDefinition());

        // Prototypes are created anew every time.
        if (prototype) {
            return define(bc.getDefinition());
        }

        // Singleton: ensure define() runs at most once per BeanContainer.
        synchronized (bc) {
            Object existing = bc.getSingleton();
            if (existing != null) {
                return existing;
            }

            Object created = define(bc.getDefinition());
            bc.setSingleton(created);
            return created;
        }
    }

    private @NotNull Optional<BeanContainer> getFirstByName(String url) {
        return bcs.values().stream().filter(bd -> url.equals(bd.getDefinition().getName())).findFirst();
    }

    @Override
    public List<Object> getBeans() {
        return bcs.values().stream().filter(bd -> !bd.getDefinition().isComponent())
                .map(BeanContainer::getSingleton)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Grammar getGrammar() {
        return grammar;
    }

    private static boolean isPrototypeScope(BeanDefinition bd) {
        if (!bd.isBean())
            return bd.isPrototype();

        return "PROTOTYPE".equalsIgnoreCase(
                bd.getNode().path("bean").path("scope").asText("SINGLETON")
        );
    }

    @Override
    public <T> List<T> getBeans(Class<T> clazz) {
        return bcs.values().stream()
                .map(BeanContainer::getSingleton)
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
        BeanContainer bc = new BeanContainer(new BeanDefinition("component", computeBeanName(beanName, uuid), null, uuid, null));
        bc.setSingleton(bean);
        bcs.put(uuid, bc);
    }

    public <T> T registerIfAbsent(Class<T> type, Supplier<T> supplier) {
        var existing = getBean(type);
        if (existing.isPresent())
            return existing.get();

        T created = supplier.get(); // outside lock

        synchronized (this) {
            return getBean(type).orElseGet(() -> {
                register(null, created);
                return created;
            });
        }
    }

    private static @NotNull String computeBeanName(String beanName, String uuid) {
        return beanName != null ? beanName : "#" + uuid;
    }

}
