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

import com.predic8.membrane.annot.Grammar;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import static com.predic8.membrane.annot.yaml.WatchAction.DELETED;
import static com.predic8.membrane.annot.yaml.WatchAction.MODIFIED;

public class BeanCache implements BeanRegistry {
    private static final Logger log = LoggerFactory.getLogger(BeanCache.class);
    private final BeanCacheObserver router;
    private final Grammar grammar;
    private final ConcurrentHashMap<String, Object> uuidMap = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<ChangeEvent> changeEvents = new ArrayBlockingQueue<>(1000);
    private Thread thread;

    interface ChangeEvent {
    }

    record BeanDefinitionChanged(BeanDefinition bd) implements ChangeEvent {
    }

    record StaticConfigurationLoaded() implements ChangeEvent {
    }

    // uid -> bean definition
    private final Map<String, BeanDefinition> bds = new ConcurrentHashMap<>();
    private final Set<String> uidsToActivate = ConcurrentHashMap.newKeySet();

    public BeanCache(BeanCacheObserver router, Grammar grammar) {
        this.router = router;
        this.grammar = grammar;
    }

    public void start() {
        thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    ChangeEvent changeEvent = changeEvents.take();
                    if (changeEvent instanceof StaticConfigurationLoaded) {
                        activationRun();
                        router.handleAsynchronousInitializationResult(uidsToActivate.isEmpty());
                        continue;
                    }
                    if (changeEvent instanceof BeanDefinitionChanged(BeanDefinition bd)) {
                        handle(bd);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }

        });
        thread.start();
    }

    public void stop() {
        if (thread != null)
            thread.interrupt();
    }

    public Object define(BeanDefinition bd) throws IOException, ParsingException {
        log.debug("defining bean: {}", bd.getNode());

        return GenericYamlParser.readMembraneObject(bd.getKind(),
                grammar,
                bd.getNode(),
                this);
    }

    /**
     * May be called from multiple threads.
     */
    public void handle(WatchAction action, JsonNode node) {
        changeEvents.add(new BeanDefinitionChanged(new BeanDefinition(action, node)));
    }

    /**
     * May be called from multiple threads.
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

        if (router.isActivatable(bd))
            uidsToActivate.add(bd.getUid());

        if (changeEvents.isEmpty())
            activationRun();
    }

    public void activationRun() {
        Set<String> uidsToRemove = new HashSet<>();
        for (String uid : uidsToActivate) {
            BeanDefinition bd = bds.get(uid);
            try {
                Object bean = define(bd);
                bd.setBean(bean);

                Object oldBean = null;
                if (bd.getAction() == MODIFIED || bd.getAction() == DELETED)
                    oldBean = uuidMap.get(bd.getUid());

                router.handleBeanEvent(bd, bean, oldBean);

                if (bd.getAction() == WatchAction.ADDED || bd.getAction() == MODIFIED)
                    uuidMap.put(bd.getUid(), bean);
                if (bd.getAction() == DELETED) {
                    uuidMap.remove(bd.getUid());
                    bds.remove(bd.getUid());
                }
                uidsToRemove.add(bd.getUid());
            } catch (Throwable e) {
                log.error("Could not handle {} {}/{}", bd.getAction(), bd.getNamespace(), bd.getName(), e);
            }
        }
        for (String uid : uidsToRemove)
            uidsToActivate.remove(uid);
    }

    @Override
    public Object resolveReference(String url) {
        Optional<BeanDefinition> obd = getFirstByName(url);
        if (!obd.isPresent())
            throw new RuntimeException("Reference " + url + " not found");

        BeanDefinition bd = obd.get();
        Object envelope = null;
        if (bd.getBean() != null)
            envelope = bd.getBean();
        if (envelope == null) {
            try {
                envelope = define(bd);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (!"prototype".equals(bd.getScope()))
                bd.setBean(envelope);
        }
        return envelope;
        // TODO
//            if (spec instanceof Bean)
//                return ((Bean) spec).getBean();
    }

    private @NotNull Optional<BeanDefinition> getFirstByName(String url) {
        return bds.values().stream().filter(bd -> bd.getName().equals(url)).findFirst();
    }

    @Override
    public List<Object> getBeans() {
        return bds.values().stream().map(BeanDefinition::getBean).filter(Objects::nonNull).toList();
    }

    @Override
    public <T> List<T> getBeansOfType(Class<T> clazz) {
        return getBeans().stream().filter(clazz::isInstance).map(clazz::cast).toList();
    }
}
