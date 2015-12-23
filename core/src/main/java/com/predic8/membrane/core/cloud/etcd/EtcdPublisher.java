/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.cloud.etcd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.cloud.ExponentialBackoff;
import com.predic8.membrane.core.cloud.ExponentialBackoff.Job;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;

@MCElement(name = "etcdPublisher")
public class EtcdPublisher implements ApplicationContextAware, Lifecycle {
    private static final Log log = LogFactory.getLog(EtcdPublisher.class.getName());

    private ApplicationContext context;
    private HashMap<String, ArrayList<String>> modulesToUUIDs = new HashMap<String, ArrayList<String>>();
    private HashSet<EtcdNodeInformation> nodesFromConfig = new HashSet<EtcdNodeInformation>();
    private int ttl;
    private String baseUrl;
    private String baseKey;
    private Router router;
    private int retryDelayMin = 10 * 1000;
    private int retryDelayMax = 10 * 60 * 1000;
    private double expDelayFactor = 2.0d;
    private Job jobPublishToEtcd = new Job() {

        @Override
        public boolean run() throws Exception {
            return publishToEtcd();
        }
    };

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * @description URL for etcd
     * @default "http://localhost:4001"
     */
    @MCAttribute
    public void setBaseUrl(String baseURL) {
        this.baseUrl = baseURL;
    }

    public String getBaseKey() {
        return baseKey;
    }

    /**
     * @description Key/Directory
     * @default "/asa/lb"
     */
    @MCAttribute
    public void setBaseKey(String baseKey) {
        this.baseKey = baseKey;
    }

    public int getTtl() {
        return ttl;
    }

    /**
     * @description time to live of etcd data
     * @default 300
     */
    @MCAttribute
    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    private Thread ttlRefreshThread = new Thread(new Runnable() {

        @Override
        public void run() {
            try {
                while (true) {
                    boolean connectionLost = false;
                    for (String module : modulesToUUIDs.keySet()) {
                        for (String uuid : modulesToUUIDs.get(module)) {
                            try {
                                if(!EtcdRequest.create(baseUrl, baseKey,module).uuid(uuid).refreshTTL(ttl).sendRequest().is2XX()){

                                    log.warn("Could not contact etcd at " + baseUrl);
                                    connectionLost = true;
                                }
                            } catch (Exception e) {
                                connectionLost = true;
                            }
                        }
                    }
                    if (connectionLost) {
                        log.warn("Connection lost to etcd");
                        ExponentialBackoff.retryAfter(retryDelayMin, retryDelayMax, expDelayFactor,
                                "Republish from thread after failed ttl refresh", jobPublishToEtcd);
                    }
                    Thread.sleep(Math.max(0,(getTtl() - 2) * 1000));
                }
            } catch (Exception ignored) {
            }
        }

    });

    @Override
    public boolean isRunning() {
        return false;
    }

    public void readConfig() {
        nodesFromConfig.clear();
        for (Rule rule : router.getRuleManager().getRules()) {

            if (!(rule instanceof ServiceProxy))
                continue;

            ServiceProxy sp = (ServiceProxy) rule;

            if (sp.getPath() == null)
                continue;

            nodesFromConfig.add(new EtcdNodeInformation(sp.getPath().getValue(), "/" + UUID.randomUUID().toString(), "localhost", Integer.toString(sp.getPort()), sp.getName()));
        }

    }

    public boolean publishToEtcd() {
        try {
            for (EtcdNodeInformation node : nodesFromConfig) {
                if (!EtcdUtil.checkOK(createDirectoryWithTtl(node))) {
                    return false;
                }

                if (!EtcdUtil.checkOK(value(node, "name", node.getName()))) {
                    return false;
                }

                if (!EtcdUtil.checkOK(value(node, "port", node.getTargetPort()))) {
                    return false;
                }

                if (!EtcdUtil.checkOK(value(node, "host", node.getTargetHost()))) {
                    return false;
                }

                if (!modulesToUUIDs.containsKey(node.getModule())) {
                    modulesToUUIDs.put(node.getModule(), new ArrayList<String>());
                }
                modulesToUUIDs.get(node.getModule()).add(node.getUuid());
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private EtcdResponse createDirectoryWithTtl(EtcdNodeInformation node) {
        return EtcdUtil.createBasicRequest(baseUrl, baseKey, node.getModule()).createDir(node.getUuid())
                .ttl(ttl).sendRequest();
    }


    private EtcdResponse value(EtcdNodeInformation node, String name, String value) {
        return EtcdUtil.createBasicRequest(baseUrl, baseKey, node.getModule()).uuid(node.getUuid())
                .setValue(name, value).sendRequest();
    }

    @EventListener({ContextRefreshedEvent.class})
    @Override
    public void start() {
        if (context == null)
            throw new IllegalStateException(
                    "EtcdBasedConfigurator requires a Router. Option 1 is to call setRouter(). Option 2 is setApplicationContext() and the EBC will try to use the only Router available.");

        if (router == null) {
            router = context.getBean(Router.class);
        }
        readConfig();
        try {
            ExponentialBackoff.retryAfter(retryDelayMin, retryDelayMax, expDelayFactor, "Publish to etcd",
                    jobPublishToEtcd);
        } catch (InterruptedException ignored) {
        }
        if (!ttlRefreshThread.isAlive()) {
            ttlRefreshThread.start();
        }
    }

    @Override
    public void stop() {
        ttlRefreshThread.interrupt();
        try {
            ttlRefreshThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (String module : modulesToUUIDs.keySet()) {
            for (String uuid : modulesToUUIDs.get(module)) {
                @SuppressWarnings("unused")
                EtcdResponse respUnregisterProxy = deleteDir(module, uuid);
                // this is probably unneeded as the etcd data has ttl
                // set and will autodelete after the ttl
            }
        }
    }

    private EtcdResponse deleteDir(String module, String uuid) {
        return EtcdUtil.createBasicRequest(baseUrl, baseKey, module).uuid(uuid)
                .deleteDir().sendRequest();
    }


    @Override
    public void setApplicationContext(ApplicationContext arg0) throws BeansException {
        context = arg0;

    }

}
