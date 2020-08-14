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
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.cloud.ExponentialBackoff;
import com.predic8.membrane.core.cloud.etcd.EtcdNodeInformation;
import com.predic8.membrane.core.cloud.etcd.EtcdRequest;
import com.predic8.membrane.core.cloud.etcd.EtcdResponse;
import com.predic8.membrane.core.config.spring.BaseLocationApplicationContext;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.interceptor.apimanagement.ApiManagementConfiguration;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

@MCElement(name="etcdRegistryApiConfig")
public class EtcdRegistryApiConfig implements Lifecycle, ApplicationContextAware, ApiConfig, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EtcdRegistryApiConfig.class.getName());

    private ApplicationContext context;
    private ApiManagementConfiguration amc;
    private String url;
    private String membraneId;
    private String baseKeyPrefix = "/membrane/";
    private int ttl = 300;
    private int retryDelayMin = 10 * 1000;
    private int retryDelayMax = 10 * 60 * 1000;
    private double expDelayFactor = 2.0d;

    Thread publisher = new Thread(new Runnable() {

        int sleepTime = (ttl - 10) * 1000;

        @Override
        public void run() {
            try {
                boolean connectionLost = false;

                while (true) {
                    String baseKey = baseKeyPrefix+membraneId;
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
        String baseKey = baseKeyPrefix+membraneId;
        EtcdResponse respPublishApiUrl = EtcdRequest.create(url,baseKey,"/apiconfig").setValue("url","").sendRequest();
        if(!respPublishApiUrl.is2XX()){
            System.out.println(respPublishApiUrl.getBody());
            return false;
        }
        EtcdResponse respPublishApiFingerprint = EtcdRequest.create(url,baseKey,"/apiconfig").setValue("fingerprint","").sendRequest();
        if(!respPublishApiFingerprint.is2XX()){
            System.out.println(respPublishApiFingerprint.getBody());
            return false;
        }
        EtcdNodeInformation adminConsole = findAdminConsole();

        EtcdResponse respPublishEndpointName = EtcdRequest.create(url,baseKey,"/endpoint").setValue("name",adminConsole.getName()).sendRequest();
        if(!respPublishEndpointName.is2XX()){
            System.out.println(respPublishEndpointName.getBody());
            return false;
        }

        EtcdResponse respPublishEndpointHost = EtcdRequest.create(url,baseKey,"/endpoint").setValue("host",adminConsole.getTargetHost()).sendRequest();
        if(!respPublishEndpointHost.is2XX()){
            System.out.println(respPublishEndpointHost.getBody());
            return false;
        }

        EtcdResponse respPublishEndpointPort = EtcdRequest.create(url,baseKey,"/endpoint").setValue("port",adminConsole.getTargetPort()).sendRequest();
        if(!respPublishEndpointPort.is2XX()){
            System.out.println(respPublishEndpointPort.getBody());
            return false;
        }

        return true;
    }

    private EtcdNodeInformation findAdminConsole() {
        Object routerObj = context.getBean(Router.class);
        if(routerObj == null)
            throw new RuntimeException("Router not found, cannot publish admin console");
        Router router = (Router) routerObj;
        for (Rule r : router.getRuleManager().getRules()) {
            if (!(r instanceof AbstractServiceProxy)) continue;

            for (Interceptor i : r.getInterceptors()) {
                if (i instanceof AdminConsoleInterceptor) {
                    String name = r.getName();
                    String host = ((ServiceProxy) r).getExternalHostname();
                    if(host == null)
                        host = getLocalHostname();
                    String port = Integer.toString(((AbstractServiceProxy) r).getPort());
                    EtcdNodeInformation node = new EtcdNodeInformation(null,null,host,port,name);
                    return node;

                }
            }
        }
        throw new RuntimeException("Admin console not found but is needed. Add a service proxy with an admin console.");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void start() {
        Object routerObj = context.getBean(Router.class);
        if(routerObj == null)
            throw new RuntimeException("Router cannot be found");
        Router router = (Router) routerObj;
        membraneId = router.getId();

        try {
            log.info("Started membrane publishing");
            ExponentialBackoff.retryAfter(retryDelayMin, retryDelayMax, expDelayFactor,
                    "First publish from thread", jobPublisher);
        } catch (InterruptedException ignored) {
        }
        initAmc();
        if(!publisher.isAlive()) {
            publisher.start();
        }
    }

    public void initAmc(){
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
        amc = new ApiManagementConfiguration(workingDir,etcdUrlForAmc,membraneId);
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

    public String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try {
                return IOUtils.toString(Runtime.getRuntime().exec("hostname").getInputStream());
            } catch (IOException e1) {
                e1.printStackTrace();
                return "localhost";
            }
        }
    }
}
