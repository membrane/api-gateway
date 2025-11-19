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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.predic8.membrane.annot.K8sHelperGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.StreamStartEvent;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import static com.predic8.membrane.annot.yaml.YamlUtil.removeFirstYamlDocStartMarker;

public class BeanCache implements BeanRegistry {
    private static final Logger log = LoggerFactory.getLogger(BeanCache.class);
    private final BeanCacheObserver router;
    private final K8sHelperGenerator k8sHelperGenerator;
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final ConcurrentHashMap<String, Object> uuidMap = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<ChangeEvent> changeEvents = new ArrayBlockingQueue<>(1000);
    private Thread thread;

    interface ChangeEvent {}
    record BeanDefinitionChanged(BeanDefinition bd) implements ChangeEvent {}
    record StaticConfigurationLoaded() implements ChangeEvent {}

    // uid -> bean definition
    private final Map<String, BeanDefinition> bds = new ConcurrentHashMap<>();
    private final Set<String> uidsToActivate = ConcurrentHashMap.newKeySet();

    public BeanCache(BeanCacheObserver router, K8sHelperGenerator k8sHelperGenerator) {
        this.router = router;
        this.k8sHelperGenerator = k8sHelperGenerator;
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

    public Object define(BeanDefinition bd) throws IOException {
        String yaml = removeFirstYamlDocStartMarker(mapper.writeValueAsString(bd.getMap())); // TODO Why do we first parse than serialize than parse again?
        log.debug("defining bean: {}", yaml);

        return GenericYamlParser.readMembraneObject(bd.getKind(),
                k8sHelperGenerator,
                yaml,
                this);
    }

    /**
     * May be called from multiple threads.
     */
    public void handle(WatchAction action, Map<String,Object> m) {
        changeEvents.add(new BeanDefinitionChanged(new BeanDefinition(action, m)));
    }

    /**
     * May be called from multiple threads.
     */
    public void handle(WatchAction action, BeanDefinition bd) {
        changeEvents.add(new BeanDefinitionChanged(bd));
    }

    /**
     * Signals that all {@link ChangeEvent}s have been passed to {@link #handle(WatchAction, Map)} which originate from
     * static configuration (e.g. a file).
     */
    public void fireConfigurationLoaded() {
        changeEvents.add(new StaticConfigurationLoaded());
    }


    void handle(BeanDefinition bd) {
        if (bd.getAction() == WatchAction.DELETED)
            bds.remove(bd.getUid());
        else
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
                Object o = define(bd);
                bd.setBean(o);

                Object oldBean = null;
                if (bd.getAction() == WatchAction.MODIFIED || bd.getAction() == WatchAction.DELETED)
                    oldBean = uuidMap.get(bd.getUid());

                router.handleBeanEvent(bd, o, oldBean, bd.getAction());

                if (bd.getAction() == WatchAction.ADDED || bd.getAction() == WatchAction.MODIFIED)
                    uuidMap.put(bd.getUid(), o);
                if (bd.getAction() == WatchAction.DELETED)
                    uuidMap.remove(bd.getUid());
                uidsToRemove.add(bd.getUid());
            }
            catch (Throwable e) {
                log.error("Could not handle {} {}/{}",bd.getAction(),bd.getNamespace(),bd.getName(), e);
            }
        }
        for (String uid : uidsToRemove)
            uidsToActivate.remove(uid);
    }

    @Override
    public Object resolveReference(String url) {
        Optional<BeanDefinition> obd = bds.values().stream().filter(bd -> bd.getName().equals(url)).findFirst();
        if (obd.isPresent()) {
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
            Object spec = envelope;
            // TODO
//            if (spec instanceof Bean)
//                return ((Bean) spec).getBean();
            return spec;
        }
        throw new RuntimeException("Reference " + url + " not found");
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
