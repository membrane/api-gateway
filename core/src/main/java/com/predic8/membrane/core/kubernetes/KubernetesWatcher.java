/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.spring.K8sHelperGeneratorAutoGenerated;
import com.predic8.membrane.core.interceptor.kubernetes.KubernetesValidationInterceptor;
import com.predic8.membrane.core.kubernetes.client.*;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Creates watcher on all known CustomResourceDefinitions listed at {@link K8sHelperGeneratorAutoGenerated}
 */
public class KubernetesWatcher {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesWatcher.class);

    private final Router router;
    private final BeanCache beanCache;
    private KubernetesClient client;
    private ExecutorService executors;
    private ConcurrentHashMap<String, Closeable> watches = new ConcurrentHashMap<>();

    public KubernetesWatcher(Router router) {
        this.router = router;
        this.beanCache = new BeanCache(router);
    }

    public void start() {
        Optional<KubernetesValidationInterceptor> kvi = findK8sValidatingInterceptor();
        if (kvi.isEmpty()) {
            return;
        }

        beanCache.start();

        client = getClient();

        List<String> crds = K8sHelperGeneratorAutoGenerated.crdSingularNames;
        if (kvi.get().getResourcesList().size() > 0)
            crds = crds.stream().filter(s -> kvi.get().getResourcesList().contains(s)).collect(Collectors.toList());
        if (crds.size() > 0)
            this.executors = Executors.newFixedThreadPool(crds.size());
        List<String> namespaces = new ArrayList<>(kvi.get().getNamespacesList());
        if (namespaces.size() == 1 && "*".equals(namespaces.get(0)))
            namespaces.set(0, null);
        crds.forEach(crd -> namespaces.forEach(ns -> createWatcher(ns, crd)));
    }

    public void stop() {
        watches.values().forEach(c -> {
            try {
                c.close();
            } catch (IOException e) {
            }
        });
        beanCache.stop();
    }

    private KubernetesClient getClient() {
        return router.getKubernetesClientFactory().createClient(null);
    }

    private Optional<KubernetesValidationInterceptor> findK8sValidatingInterceptor() {
        return router.getRules().stream()
                .map(rule -> rule.getInterceptors())
                .filter(interceptors -> interceptors != null)
                .filter( i -> i != null)
                .flatMap(interceptors -> interceptors.stream())
                .filter(inter -> inter instanceof KubernetesValidationInterceptor)
                .map(inter -> (KubernetesValidationInterceptor) inter)
                .findFirst();
    }

    private boolean isInK8sCluster() {
        return System.getenv("KUBERNETES_SERVICE_HOST") != null;
    }

    @SuppressWarnings("rawtypes")
    private void createWatcher(String namespace, String crd) {
        try {
            watches.put(namespace + "/" + crd, client.watch("membrane-soa.org/v1beta1", crd, namespace, null, executors, new Watcher() {
                @Override
                public void onEvent(WatchAction action, Map m) {
                    try {
                        System.err.println(action + " " + crd + " " + ((Map)m.get("metadata")).get("namespace") + "/" + ((Map)m.get("metadata")).get("name"));

                        beanCache.handle(action, m);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClosed(@Nullable Throwable t) {
                    LOG.error("Watcher for "+crd+" closed unexpectedly, restarting...", t);
                    createWatcher(namespace, crd);
                }
            }));
            LOG.debug("Added Watcher for {}", crd);
        } catch (IOException | KubernetesApiException e) {
            // TODO: retry in 1 min
            e.printStackTrace();
        }
    }

    @SuppressWarnings("rawtypes")
    private String getUid(JSONObject json) {
        JSONObject metadata = new JSONObject((Map) json.get("metadata"));
        return (String) metadata.get("uid");
    }

    private String lowerFirstChar(String str) {
        if (str == null || str.isEmpty())
            return "";
        if (str.length() == 1)
            return str.toLowerCase();
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
}
