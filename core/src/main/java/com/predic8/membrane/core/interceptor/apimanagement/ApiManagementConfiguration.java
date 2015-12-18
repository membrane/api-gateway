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

package com.predic8.membrane.core.interceptor.apimanagement;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.predic8.membrane.core.cloud.etcd.EtcdRequest;
import com.predic8.membrane.core.cloud.etcd.EtcdResponse;
import com.predic8.membrane.core.cloud.etcd.EtcdUtil;
import com.predic8.membrane.core.interceptor.apimanagement.policy.Policy;
import com.predic8.membrane.core.interceptor.apimanagement.policy.Quota;
import com.predic8.membrane.core.interceptor.apimanagement.policy.RateLimit;
import com.predic8.membrane.core.resolver.Consumer;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ApiManagementConfiguration {

    private static String currentDir;

    private static Logger log = LogManager.getLogger(ApiManagementConfiguration.class);
    private ResolverMap resolver = null;
    private String location = null;
    private String hashLocation = null;
    private String currentHash = "";
    private ApplicationContext context;
    public HashSet<Runnable> configChangeObservers = new HashSet<Runnable>();
    private String membraneName;
    private boolean contextLost = false;

    private void notifyConfigChangeObservers(){
        for(Runnable runner : configChangeObservers){
            runner.run();
        }
    }

    public static String getCurrentDir() {
        return currentDir;
    }

    public static void setCurrentDir(String currentDir) {
        ApiManagementConfiguration.currentDir = currentDir;
    }

    public Map<String, Policy> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, Policy> policies) {
        this.policies = policies;
    }

    public Map<String, Key> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, Key> keys) {
        this.keys = keys;
    }

    private Map<String,Policy> policies = new ConcurrentHashMap<String, Policy>();
    private Map<String,Key> keys = new ConcurrentHashMap<String, Key>();

    public ApiManagementConfiguration(){
        this(System.getProperty("user.dir"),"api.yaml","membrane");
    }

    public ApiManagementConfiguration(String currentDir, String configLocation){
        this(currentDir,configLocation,"");
    }

    public ApiManagementConfiguration(String currentDir, String configLocation, String membraneName){
        if (getResolver() == null) {
            setResolver(new ResolverMap());
        }
        setCurrentDir(currentDir);
        setMembraneName(membraneName);
        setLocation(configLocation);
    }

    private Map<String,Policy> parsePolicies(Map<String,Object> yaml) {
        Map<String,Policy> result = new HashMap<String, Policy>();
        Object policies = yaml.get("policies");
        if(policies == null)
        {
            log.warn("\"policies\" keyword not found");
            return result;
        }
        List<Object> yamlPolicies = (List<Object>) policies;

        for(Object yamlPolicyObj : yamlPolicies)
        {
            if(yamlPolicyObj == null){
                continue;
            }
            LinkedHashMap<String,Object> yamlPolicy = (LinkedHashMap<String, Object>) yamlPolicyObj;

            for(Object polObj : yamlPolicy.values())
            {
                if(polObj == null){
                    continue;
                }
                LinkedHashMap<String,Object> yamlPolicyDef = (LinkedHashMap<String, Object>) polObj;
                Policy policy = new Policy();

                Object name = yamlPolicyDef.get("id");
                if(name == null){
                    log.warn("Policy object found, but no \"id\" field");
                    continue;
                }
                String policyName = (String) name;
                policy.setName(policyName);
                Object serviceProxiesObj = yamlPolicyDef.get("serviceProxy");
                if(serviceProxiesObj == null){
                    log.warn("Policy object found, but no service proxies specified ");
                    continue;
                }
                List<String> serviceProxyNames = (List<String>) serviceProxiesObj;
                for(String sp : serviceProxyNames){
                    policy.getServiceProxies().add(sp);
                }

                //Optionals like rateLimit/quota etc. follow
                Object rateLimitObj = yamlPolicyDef.get("rateLimit");
                if(rateLimitObj != null){
                    LinkedHashMap<String,Object> rateLimitData = (LinkedHashMap<String, Object>) rateLimitObj;
                        RateLimit rateLimit = new RateLimit();

                        int requests = -1;
                        Object requestsObj = rateLimitData.get("requests");
                        if(requestsObj == null){
                            log.warn("RateLimit object found, but request field is empty");
                            requests = RateLimit.REQUESTS_DEFAULT;
                        }else {
                            requests = (Integer) requestsObj;
                        }

                        int interval = -1;
                        Object intervalObj = rateLimitData.get("interval");
                        if(intervalObj == null) {
                            log.warn("RateLimit object found, but interval field is empty. Setting default: \" + RateLimit.INTERVAL_DEFAULT");
                            interval = RateLimit.INTERVAL_DEFAULT;
                        }else {
                            interval = (Integer)intervalObj;
                        }
                        rateLimit.setRequests(requests);
                        rateLimit.setInterval(interval);
                        policy.setRateLimit(rateLimit);

                }
                Object quotaObj = yamlPolicyDef.get("quota");
                if(quotaObj != null){
                    LinkedHashMap<String,Object> quota = (LinkedHashMap<String, Object>) quotaObj;
                    Object quotaSizeObj = quota.get("size");
                    long quotaNumber = 0;
                    String quotaSymbolString = "";
                    if(quotaSizeObj == null){
                        log.warn("Quota object found, but size field is empty");
                        quotaNumber = Quota.SIZE_DEFAULT;
                    }else{
                        try {
                            String quotaString = (String) quotaSizeObj;
                            quotaNumber = ((Number) NumberFormat.getInstance().parse(quotaString)).intValue();
                            quotaSymbolString = quotaString.replaceFirst(Long.toString(quotaNumber),"").toLowerCase();
                        } catch (ParseException ignored) {
                        }
                    }
                    if(quotaSymbolString.length() > 0) {
                        char quotaSymbol = quotaSymbolString.charAt(0);
                        switch (quotaSymbol) {
                            case 'g': quotaNumber *= 1024;
                            case 'm': quotaNumber *= 1024;
                            case 'k': quotaNumber *= 1024;
                            case 'b':
                            default:
                        }
                    }
                    Object quotaIntervalObj = quota.get("interval");
                    int quotaInterval = 0;
                    if(quotaIntervalObj == null){
                        log.warn("Quota object found, but interval field is empty");
                        quotaInterval = Quota.INTERVAL_DEFAULT;
                    }else {
                        quotaInterval = (Integer) quotaIntervalObj;
                    }

                    Quota q = new Quota();
                    q.setSize(quotaNumber);
                    q.setInterval(quotaInterval);
                    policy.setQuota(q);
                }

                result.put(policyName,policy);
            }
        }
        return result;
    }

    private void parseAndConstructConfiguration(InputStream is){
        String yamlSource = null;
        try {
            yamlSource = IOUtils.toString(is);
        } catch (IOException e) {
            log.warn("Could not read stream");
            return;
        }
        YAMLMapper mapper = new YAMLMapper();
        Map<String,Object> yaml = null;
        try {

            yaml = mapper.readValue(yamlSource, Map.class);
        } catch (IOException e) {
            log.warn("Could not parse yaml");
            return;
        }
        setPolicies(parsePolicies(yaml));
        setKeys(parsePoliciesForKeys(yaml));
        log.info("Configuration loaded.");
        notifyConfigChangeObservers();
    }

    private Map<String,Key> parsePoliciesForKeys(Map<String, Object> yaml) {
        Map<String,Key> result = new HashMap<String, Key>();

        // assumption: the yaml is valid

        List<Object> keys = (List<Object>) yaml.get("keys");
        for(Object keyObject : keys){
            LinkedHashMap<String,Object> key = (LinkedHashMap<String, Object>) keyObject;
            Key keyRes = new Key();
            String keyName = (String) key.get("key");
            keyRes.setName(keyName);
            List<Object> policiesForKey = (List<Object>) key.get("policies");
            HashSet<Policy> policies = new HashSet<Policy>();
            for(Object polObj : policiesForKey){
                String policyName = (String) polObj;
                Policy p = this.policies.get(policyName);
                policies.add(p);
            }
            keyRes.setPolicies(policies);
            result.put(keyName,keyRes);
        }

        return result;
    }


    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
        if(getResolver() != null)
        {
            updateAfterLocationChange(location);
        }
    }

    private boolean isLocalFile(String location){
        boolean isFile = false;
        try {
            URI uri = new URI(location);
            if(uri.getScheme() == null || uri.getScheme().equals("file")){
                isFile = true;
            }
        } catch (URISyntaxException ignored) {
        }
        return isFile;
    }

    public void updateAfterLocationChange(String location){
        if(!isLocalFile(location)){
            log.info("Loading configuration from [" + location + "]");
            if(location.startsWith("etcd")){
                handleEtcd(location);
            }else {
                try {
                    parseAndConstructConfiguration(getResolver().resolve(location));
                } catch (ResourceRetrievalException e) {
                    log.error("Could not retrieve resource");
                    return;
                }
            }
            return;
        }else {
            final String newLocation = ResolverMap.combine(getCurrentDir(), location);
            log.info("Loading configuration from [" + newLocation + "]");
            InputStream is = null;
            try {
                is = getResolver().resolve(newLocation);
            } catch (ResourceRetrievalException e) {
                log.error("Could not retrieve resource");
                return;
            }
            parseAndConstructConfiguration(is);
            try {
                getResolver().observeChange(newLocation, new Consumer<InputStream>() {
                    @Override
                    public void call(InputStream inputStream) {
                        log.info("Loading configuration from [" + newLocation + "]");
                        parseAndConstructConfiguration(inputStream);
                        try {
                            getResolver().observeChange(newLocation, this);
                        } catch (ResourceRetrievalException ignored) {
                        }
                    }
                });
            } catch (Exception warn) {
                URL url = null;
                try {
                    url = new URL(newLocation);
                } catch (MalformedURLException ignored) {
                }
                String schema = "";
                if (url != null) {
                    schema = url.getProtocol();
                }
                log.warn("Could not observe AMC location for " + schema);
            }
        }
    }

    private Thread etcdConfigFingerprintLongPollThread;

    private void handleEtcd(String location) {
        // assumption: location is of type "etcd://[url]"

        final String etcdLocation = location.substring(7);

        final String baseKey = "/gateways/" + getMembraneName();

        EtcdResponse respGetConfigUrl = EtcdUtil.createBasicRequest(etcdLocation,baseKey,"/apiconfig").getValue("url").sendRequest();
        if(!EtcdUtil.checkOK(respGetConfigUrl)){
            log.warn("Could not get config url at " + etcdLocation);
            return;
        }
        final String configLocation = respGetConfigUrl.getValue();
        setLocation(configLocation); // this gets the resource and loads the config

        etcdConfigFingerprintLongPollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if(!EtcdRequest.create(etcdLocation,baseKey,"/apiconfig").getValue("fingerprint").longPoll().sendRequest().is2XX()){
                    log.warn("Could not get config fingerprint at " + etcdLocation);
                    return;
                }
                if(!getContextLost()) {
                    setLocation(configLocation);
                }
            }
        });
        etcdConfigFingerprintLongPollThread.start();



    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public static final String DEFAULT_RESOLVER_NAME = "resolverMap";

    public void start() {
        try {
            if (getResolver() == null) {
                Object defaultResolver = null;
                try {
                    if(context != null) {
                        defaultResolver = context.getBean("resolverMap");
                        if (defaultResolver != null) {
                            setResolver((ResolverMap) defaultResolver);
                        }
                    }else{
                        log.error("Context not set");
                    }
                } catch (Exception ignored) {
                }
                if (getResolver() == null) {
                    setResolver(new ResolverMap());
                }
            }
            if(this.hashLocation == null) {
                updateAfterLocationChange(this.location);
            }else {
                setHashLocation(this.hashLocation);
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public void stop() {

    }

    public boolean isRunning() {
        return false;
    }

    public ResolverMap getResolver() {
        return resolver;
    }

    public void setResolver(ResolverMap resolver) {
        this.resolver = resolver;
    }

    public String getHashLocation() {
        return hashLocation;
    }

    public void setHashLocation(final String hashLocation) throws IOException {
        this.hashLocation = hashLocation;
        if(getResolver() != null){
            if (!isLocalFile(hashLocation)) {
                this.hashLocation = hashLocation;
                String newHash = IOUtils.toString(resolver.resolve(hashLocation));
                if (!currentHash.equals(newHash)) {
                    currentHash = newHash;
                    updateAfterLocationChange(this.location);
                    getResolver().observeChange(hashLocation, new Consumer<InputStream>() {
                        @Override
                        public void call(InputStream inputStream) {
                            try {
                                currentHash = IOUtils.toString(resolver.resolve(hashLocation));
                            } catch (IOException ignored) {
                            }
                            updateAfterLocationChange(ApiManagementConfiguration.this.location);
                            try {
                                getResolver().observeChange(hashLocation,this);
                            } catch (ResourceRetrievalException ignored) {
                            }
                        }
                    });
                }
            }
        }
    }

    public void setMembraneName(String membraneName) {
        this.membraneName = membraneName;
    }

    public String getMembraneName() {
        return membraneName;
    }

    public boolean getContextLost() {
        return contextLost;
    }

    public void setContextLost(boolean contextLost) {
        this.contextLost = contextLost;
    }

    public void shutdown() {
        setContextLost(true);
    }
}
