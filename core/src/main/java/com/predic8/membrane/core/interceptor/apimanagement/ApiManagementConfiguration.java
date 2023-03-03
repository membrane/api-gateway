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
import com.predic8.membrane.core.interceptor.apimanagement.policy.Policy;
import com.predic8.membrane.core.interceptor.apimanagement.policy.Quota;
import com.predic8.membrane.core.interceptor.apimanagement.policy.RateLimit;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;
import com.predic8.membrane.core.util.functionalInterfaces.Function;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ApiManagementConfiguration {
    private static String currentDir;

    private static Logger log = LoggerFactory.getLogger(ApiManagementConfiguration.class);
    private ResolverMap resolver = null;
    private String location = null;
    private String hashLocation = null;
    private String currentHash = "";
    private ApplicationContext context;
    public HashSet<Runnable> configChangeObservers = new HashSet<>();
    String etcdPathPrefix = "/membrane/";
    private String membraneName;
    private boolean contextLost = false;
    private Thread etcdConfigFingerprintLongPollThread;

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

    private Map<String,Policy> policies = new ConcurrentHashMap<>();
    private Map<String,Key> keys = new ConcurrentHashMap<>();

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

        try {
            updateAfterLocationChange();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String,Policy> parsePolicies(Map<String,Object> yaml) {
        Map<String,Policy> result = new HashMap<>();
        Object policies = yaml.get("policies");
        if(policies == null)
        {
            log.warn("No policies in policy file");
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

                        int requests = parseInteger(rateLimitData.get("requests"),RateLimit.REQUESTS_DEFAULT);
                        int interval = parseInteger(rateLimitData.get("interval"),RateLimit.INTERVAL_DEFAULT);

                        rateLimit.setRequests(requests);
                        rateLimit.setInterval(interval);
                        policy.setRateLimit(rateLimit);

                }
                Object quotaObj = yamlPolicyDef.get("quota");
                if(quotaObj != null){
                    LinkedHashMap<String,Object> quota = (LinkedHashMap<String, Object>) quotaObj;
                    Object quotaSizeObj = quota.get("size");
                    long quotaNumber = getQuotaNumber(quotaSizeObj);
                    int quotaInterval = parseInteger(quota.get("interval"),Quota.INTERVAL_DEFAULT);

                    Quota q = new Quota();
                    q.setSize(quotaNumber);
                    q.setInterval(quotaInterval);
                    policy.setQuota(q);
                }

                parseUnauthenticatedField(yamlPolicyDef,policy);


                result.put(policyName,policy);
            }
        }
        return result;
    }

    private void parseUnauthenticatedField(LinkedHashMap<String, Object> yamlPolicyDef, Policy policy) {
        policy.setUnauthenticated(parseBoolean(yamlPolicyDef.get("unauthenticated"),false));
    }


    private <T> T StringToTypeConverter(Object obj, T defObj, Function<String,T> stringToTypeConverter){
        if(obj == null)
            return defObj;
        if(obj instanceof String)
            if(((String)obj).isEmpty())
                return defObj;
            else
                return stringToTypeConverter.call((String) obj);
        try {
            return (T) obj;
        }catch(Exception ignored2){
            log.error("Could not parse policies file. Please make sure that the file is valid.");
            return defObj;
        }
    }

    private String parseString(Object obj, String defObj){
        return StringToTypeConverter(obj,defObj, (value) -> value);
    }

    private boolean parseBoolean(Object obj, Boolean defObj){
        return StringToTypeConverter(obj,defObj, Boolean::parseBoolean);
    }

    private long getQuotaNumber(Object quotaSizeObj) {
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
                quotaNumber = Quota.SIZE_DEFAULT;
            }
        }
        if(quotaSymbolString.length() > 0) {
            switch (quotaSymbolString) {
                case "gb":
                case "g": quotaNumber *= 1024;
                case "mb":
                case "m": quotaNumber *= 1024;
                case "kb":
                case "k": quotaNumber *= 1024;
                case "b":
                default:
            }
        }
        return quotaNumber;
    }

    private int parseInteger(Object obj, int defaultValue){
        return StringToTypeConverter(obj,defaultValue, Integer::parseInt);
    }

    private void parseAndConstructConfiguration(InputStream is) throws IOException {
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
        is.close();
        log.info("Configuration loaded. Notifying observers");
        notifyConfigChangeObservers();
    }

    public HashSet<Policy> getUnauthenticatedPolicies() {
        HashSet<Policy> result = new HashSet<>();
        for(Policy p : getPolicies().values())
            if(p.isUnauthenticated())
                result.add(p);
        return result;
    }

    public void addIpAsApiKeyIfNeeded(String ip) {
        if(!getKeys().containsKey(ip)){
            Key key = new Key();
            key.setName(ip);
            key.setExpiration(null);
            key.setPolicies(getUnauthenticatedPolicies());
            getKeys().put(ip,key);
        }
    }

    private Map<String,Key> parsePoliciesForKeys(Map<String, Object> yaml) {
        Map<String,Key> result = new HashMap<>();

        // assumption: the yaml is valid

        List<Object> keys = (List<Object>) yaml.get("keys");
        if(keys == null) {
            log.info("No API keys in policy file");
            return result;
        }
        for(Object keyObject : keys){
            LinkedHashMap<String,Object> key = (LinkedHashMap<String, Object>) keyObject;
            Key keyRes = new Key();
            String keyName = (String) key.get("key");
            keyRes.setName(keyName);

            parseExpiration(key,keyRes);

            List<Object> policiesForKey = (List<Object>) key.get("policies");
            HashSet<Policy> policies = new HashSet<>();
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

    private void parseExpiration(LinkedHashMap<String, Object> keyYaml, Key key) {
        String expirationString = parseString(keyYaml.get("expires"),null);
        Instant expiration = null;
        if(expirationString != null)
            try {
                expiration = Instant.parse(expirationString);
            }catch(Exception e){
                log.error("Could not read expiration");
            }
        key.setExpiration(expiration);
    }


    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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

    public void updateAfterLocationChange() throws IOException {
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
            if(location.isEmpty())
                location = "api.yaml";
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
                getResolver().observeChange(newLocation, new Consumer<>() {
                    @Override
                    public void call(InputStream inputStream) {
                        log.info("Loading configuration from [" + newLocation + "]");
                        if (!getContextLost()) {
                            try {
                                parseAndConstructConfiguration(inputStream);
                                getResolver().observeChange(newLocation, this);
                            } catch (IOException ignored) {
                            }
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

    private void handleEtcd(String location) throws IOException {
        // assumption: location is of type "etcd://[url]"

        final String etcdLocation = location.substring(7);

        final String baseKey = etcdPathPrefix + getMembraneName();

        EtcdResponse respGetConfigUrl = EtcdRequest.create(etcdLocation,baseKey,"/apiconfig").getValue("url").sendRequest();
        if(!respGetConfigUrl.is2XX()){
            log.warn("Could not get config url at " + etcdLocation + baseKey + "/apiconfig");
            return;
        }
        final String configLocation = respGetConfigUrl.getValue();
        setLocation(configLocation); // this gets the resource and loads the config
        updateAfterLocationChange();
        setLocation(location);

        if(etcdConfigFingerprintLongPollThread == null) {

            etcdConfigFingerprintLongPollThread = new Thread(() -> {
                try {
                    while (!getContextLost()) {
                        if (!EtcdRequest.create(etcdLocation, baseKey, "/apiconfig").getValue("fingerprint").longPoll().sendRequest().is2XX()) {
                            log.warn("Could not get config fingerprint at " + etcdLocation);
                        }
                        if (!getContextLost()) {
                            log.info("Noticed configuration change, updating...");
                            updateAfterLocationChange();
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            etcdConfigFingerprintLongPollThread.start();
        }



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
                updateAfterLocationChange();
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
        //afterHashLocationSet(hashLocation);
    }

    /*private void afterHashLocationSet(final String hashLocation) throws IOException {
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
    }*/

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
