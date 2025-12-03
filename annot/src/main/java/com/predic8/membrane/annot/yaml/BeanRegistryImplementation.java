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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.annot.yaml.BeanDefinition.create4Kubernetes;
import static com.predic8.membrane.annot.yaml.WatchAction.*;

public class BeanRegistryImplementation implements BeanRegistry {

    private static final Logger log = LoggerFactory.getLogger(BeanRegistryImplementation.class);

    private final BeanCacheObserver observer;
    private final Grammar grammar;

    // To prevent multiple threads from starting the thread concurrently.
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * TODO Rename give meaningful name
     */
    private final ConcurrentHashMap<String, Object> uuidMap = new ConcurrentHashMap<>();

    private final BlockingQueue<ChangeEvent> changeEvents = new LinkedBlockingDeque<>();

    /**
     * TODO Remove
     */
    private Thread thread;


    // uid -> bean definition
    private final Map<String, BeanDefinition> bds = new ConcurrentHashMap<>();
    private final Set<String> uidsToActivate = ConcurrentHashMap.newKeySet();

    public BeanRegistryImplementation(BeanCacheObserver observer, Grammar grammar) {
        this.observer = observer;
        this.grammar = grammar;
    }

    public void registerBeanDefinitions(List<BeanDefinition> bds) {
        bds.forEach(bd -> handle(ADDED, bd));
        fireConfigurationLoaded(); // Only put event in queue
        start();
    }

    public void start() {
        // For CLI thread is not needed. Migrate thread into KubernetesWatcher

        if (!started.compareAndSet(false, true))
            return;

 //       thread = Thread.ofVirtual().name("bean-activator").start(() -> {
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
   //     });
    }

    public void stop() {
        if (thread != null)
            thread.interrupt();
    }

    private Object define(BeanDefinition bd) throws IOException, ParsingException {
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
        changeEvents.add(new BeanDefinitionChanged(create4Kubernetes(action, node)));
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

        if (observer.isActivatable(bd))
            uidsToActivate.add(bd.getUid());

        if (changeEvents.isEmpty())
            activationRun();
    }

    // @TODO
    // applies all pending activations. But:
    // - It reads from shared maps modified in other threads without locking.
    // - It does not check whether MODIFIED events require redefinition.
    // Mixing ADD/MODIFY/DELETE logic is hard to follow.
    // Should we Separate event processing from bean activation?
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
            }
        }
        for (String uid : uidsToRemove)
            uidsToActivate.remove(uid);
    }

    /**
     * TODO: Issues:
     * - Defining bean lazily inside resolve - Hard to reason about lifecycle.
     */
    @Override
    public Object resolveReference(String url) {
        BeanDefinition bd = getFirstByName(url).orElseThrow(() -> new RuntimeException("Reference %s not found".formatted(url)));

        Object envelope = null;
        if (bd.getBean() != null)
            envelope = bd.getBean();
        if (envelope == null) {
            try {
                envelope = define(bd);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (!bd.isPrototype())
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
    public Grammar getGrammar() {
        return grammar;
    }
}
