/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.apimanagement.apiconfig;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.cloud.ExponentialBackoff;
import com.predic8.membrane.core.cloud.etcd.EtcdRequest;
import com.predic8.membrane.core.cloud.etcd.EtcdResponse;
import com.predic8.membrane.core.config.spring.BaseLocationApplicationContext;
import com.predic8.membrane.core.interceptor.apimanagement.ApiManagementConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

import java.net.MalformedURLException;
import java.net.URL;

@MCElement(name="etcdRegistryApiConfig")
public class EtcdRegistryApiConfig implements Lifecycle, ApplicationContextAware, ApiConfig, DisposableBean {

    private static final Log log = LogFactory.getLog(EtcdRegistryApiConfig.class.getName());

    private ApplicationContext context;
    private ApiManagementConfiguration amc;
    private String url;
    private String membrane;
    private String endpoint;
    private int ttl = 300;
    private int retryDelayMin = 10 * 1000;
    private int retryDelayMax = 10 * 60 * 1000;
    private double expDelayFactor = 2.0d;

    Thread publisher = new Thread(new Runnable() {

        int sleepTime = (ttl - 10) * 1000;

        @Override
        public void run() {
            try {
                ExponentialBackoff.retryAfter(retryDelayMin, retryDelayMax, expDelayFactor,
                        "First publish from thread", jobPublisher);

                boolean connectionLost = false;

                while (true) {
                    String baseKey = "/gateways/"+membrane;
                    EtcdResponse respTTLDirRefresh = EtcdRequest.create(url, baseKey, "")
                            .refreshTTL(ttl).sendRequest();
                    if (!respTTLDirRefresh.is2XX()) {
                        log.warn("Could not contact etcd at " + url);
                        connectionLost = true;
                    }

                    Thread.sleep(sleepTime);
                    if (connectionLost) {
                        log.warn("Connection lost to etcd");
                        ExponentialBackoff.retryAfter(retryDelayMin, retryDelayMax, expDelayFactor,
                                "Republish from thread after failed ttl refresh", jobPublisher);
                    }
                }
            } catch (InterruptedException ignored) {
            }
        }
    });

    ExponentialBackoff.Job jobPublisher = new ExponentialBackoff.Job() {
        @Override
        public boolean run() throws Exception {
            return publishToEtcd();
        }
    };

    private boolean publishToEtcd() {
        String baseKey = "/gateways/"+membrane;
        EtcdResponse respPublishEndpoint = EtcdRequest.create(url,baseKey,"").setValue("endpoint",endpoint).sendRequest();
        if(!respPublishEndpoint.is2XX()){
            return false;
        }
        EtcdResponse respPublishApiUrl = EtcdRequest.create(url,baseKey,"/apiconfig").setValue("url","http://localhost:8081/api/api.yaml").sendRequest();
        if(!respPublishApiUrl.is2XX()){
            return false;
        }
        EtcdResponse respPublishApiFingerprint = EtcdRequest.create(url,baseKey,"/apiconfig").setValue("fingerprint","").sendRequest();
        if(!respPublishApiFingerprint.is2XX()){
            return false;
        }
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void start() {
        //TODO fill endpoint with useful info instead of this mockup
        endpoint = "123.456.789.1";

        if(!publisher.isAlive()) {
            log.info("Started membrane publishing");
            publisher.start();
        }
        String workingDir = ((BaseLocationApplicationContext)context).getBaseLocation();
        String etcdUrlForAmc = null;
        URL u = null;
        try {
            u = new URL(getUrl());

        } catch (MalformedURLException e) {
            try {
                u = new URL("http://" + getUrl());
            } catch (MalformedURLException ignored) {
            }
        }
        if(u == null){
            log.error("Url malformed: " + getUrl());
        }
        etcdUrlForAmc = "etcd://" + u.getHost() + ":" + u.getPort();
        amc = new ApiManagementConfiguration(workingDir,etcdUrlForAmc,getMembrane());
    }

    @Override
    public void stop() {
        amc.shutdown();
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description the url to where details are published
     * @default http://localhost:4001
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public String getMembrane() {
        return membrane;
    }

    /**
     * @description name for this membrane instance
     * @default membrane
     */
    @MCAttribute
    public void setMembrane(String membrane) {
        this.membrane = membrane;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public int getTtl() {
        return ttl;
    }

    /**
     * @description ttl in seconds
     * @default 300
     */
    @MCAttribute
    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    @Override
    public ApiManagementConfiguration getConfiguration() {
        return amc;
    }

    @Override
    public void destroy() throws Exception {
        stop();
    }
}
