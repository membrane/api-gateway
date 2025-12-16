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
package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.Grammar;
import com.predic8.membrane.annot.bean.BeanFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import static com.predic8.membrane.annot.yaml.BeanDefinition.create4Kubernetes;
import static com.predic8.membrane.annot.yaml.WatchAction.*;

public class BeanRegistryImplementation implements BeanRegistry {

    private static final Logger log = LoggerFactory.getLogger(BeanRegistryImplementation.class);

    private final BeanCacheObserver observer;
    private final Grammar grammar;

    /**
     * TODO Rename give meaningful name
     */
    private final ConcurrentHashMap<String, Object> uuidMap = new ConcurrentHashMap<>(); // Order is here not critical

    private final BlockingQueue<ChangeEvent> changeEvents = new LinkedBlockingDeque<>();

    // uid -> bean definition
    private final Map<String, BeanDefinition> bds = new ConcurrentHashMap<>(); // Order is not critical. Order is determined by uidsToActivate
    private final Set<String> uidsToActivate = new LinkedHashSet<>(); // Provides order

    public BeanRegistryImplementation(BeanCacheObserver observer, Grammar grammar) {
        this.observer = observer;
        this.grammar = grammar;
    }

    public void registerBeanDefinitions(List<BeanDefinition> bds) {
        bds.forEach(bd -> handle(ADDED, bd));
        fireConfigurationLoaded(); // Only put event in the queue
    }

    public void start() {
        while (!changeEvents.isEmpty()) {
            try {
                ChangeEvent changeEvent = changeEvents.take();
                if (changeEvent instanceof StaticConfigurationLoaded) {
                    activationRun();
                    observer.handleAsynchronousInitializationResult(uidsToActivate.isEmpty());
                    continue;
                }
                if (changeEvent instanceof BeanDefinitionChanged(BeanDefinition bd)) {
                    handle(bd);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private Object define(BeanDefinition bd)  {
        log.debug("defining bean: {}", bd.getNode());
        try {
            if ("bean".equals(bd.getKind())) {
                return new BeanFactory(this).createFromNode(bd.getNode().path("bean"));
            }
            return GenericYamlParser.readMembraneObject(bd.getKind(),
                    grammar,
                    bd.getNode(),
                    this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * May be called from multiple threads.
     */
    public void handle(WatchAction action, JsonNode node) {
        changeEvents.add(new BeanDefinitionChanged(create4Kubernetes(action, node)));
    }

    /**
     * May be called from multiple threads.
     * TODO remove action?
     */
    public void handle(WatchAction action, BeanDefinition bd) {
        changeEvents.add(new BeanDefinitionChanged(bd));
    }

    /**
     * Signals that all {@link ChangeEvent}s have been passed to {@link #handle(WatchAction, JsonNode)} which originate from
     * static configuration (e.g. a file).
     */
    public void fireConfigurationLoaded() {
        changeEvents.add(new StaticConfigurationLoaded());
    }

    void handle(BeanDefinition bd) {
        // Keep the latest BeanDefinition for all actions so activationRun
        // can see both metadata and the action (including DELETED).
        bds.put(bd.getUid(), bd);

        if (isNotComponent(bd) && observer.isActivatable(bd)) {
            uidsToActivate.add(bd.getUid());
        }

        if (changeEvents.isEmpty())
            activationRun();
    }

    private void activationRun() {
        Set<String> uidsToRemove = new HashSet<>();
        for (String uid1 : uidsToActivate) {
            BeanDefinition bd = bds.get(uid1);
            try {
                Object bean = define(bd);
                bd.setBean(bean);

                Object oldBean = null;
                if (bd.getAction() == MODIFIED || bd.getAction() == DELETED)
                    oldBean = uuidMap.get(bd.getUid());

                // e.g. inform router about new proxy
                observer.handleBeanEvent(bd, bean, oldBean);

                if (bd.getAction() == ADDED || bd.getAction() == MODIFIED)
                    uuidMap.put(bd.getUid(), bean);
                if (bd.getAction() == DELETED) {
                    uuidMap.remove(bd.getUid());
                    bds.remove(bd.getUid());
                }
                uidsToRemove.add(bd.getUid());
            } catch (Exception e) {
                log.error("Could not handle {} {}/{}", bd.getAction(), bd.getNamespace(), bd.getName(), e);
                throw new RuntimeException(e);
            }
        }
        for (String uid : uidsToRemove)
            uidsToActivate.remove(uid);
    }

    @Override
    public Object resolveReference(String url) {
        BeanDefinition bd = getFirstByName(url).orElseThrow(() -> new RuntimeException("Reference %s not found".formatted(url)));

        boolean prototype = isPrototypeScope(bd);

        if (!prototype && bd.getBean() != null)
            return bd.getBean();

        Object instance = define(bd);

        if (!prototype)
            bd.setBean(instance);

        return instance;
    }

    private @NotNull Optional<BeanDefinition> getFirstByName(String url) {
        return bds.values().stream().filter(bd -> bd.getName().equals(url)).findFirst();
    }

    @Override
    public List<Object> getBeans() {
        return bds.values().stream().filter(bd -> bd.getName() == null || isNotComponent(bd)).map(BeanDefinition::getBean).filter(Objects::nonNull).toList();
    }

    private static boolean isNotComponent(BeanDefinition bd) {
        String name = bd.getName();
        return name == null || !name.startsWith("#/components/");
    }

    @Override
    public Grammar getGrammar() {
        return grammar;
    }

    private static boolean isPrototypeScope(BeanDefinition bd) {
        if (!"bean".equals(bd.getKind()))
            return bd.isPrototype();

        return "PROTOTYPE".equalsIgnoreCase(
                bd.getNode().path("bean").path("scope").asText("SINGLETON")
        );
    }
}
