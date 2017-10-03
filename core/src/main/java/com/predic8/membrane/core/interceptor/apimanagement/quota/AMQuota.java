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

package com.predic8.membrane.core.interceptor.apimanagement.quota;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apimanagement.ApiManagementConfiguration;
import com.predic8.membrane.core.interceptor.apimanagement.Key;
import com.predic8.membrane.core.interceptor.apimanagement.policy.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unfinished
 */
@MCElement(name="amQuota")
public class AMQuota {

    private static Logger log = LoggerFactory.getLogger(AMQuota.class);
    private ApiManagementConfiguration amc;

    public ConcurrentHashMap<String, ApiKeyByteCounter> keyByteCounter = new ConcurrentHashMap<String, ApiKeyByteCounter>();
    public ConcurrentHashMap<String, PolicyQuota> policyQuotas = new ConcurrentHashMap<String, PolicyQuota>();

    public ApiManagementConfiguration getAmc() {
        return amc;
    }

    private Runnable observer = new Runnable() {
        @Override
        public void run() {
            log.info("Getting new config");
            fillPolicyQuotas();
            keyByteCounter = new ConcurrentHashMap<String, ApiKeyByteCounter>();
        }
    };

    public void setAmc(ApiManagementConfiguration amc) {
        if(this.amc != null) {
            this.amc.configChangeObservers.remove(observer);
        }
        this.amc = amc;
        fillPolicyQuotas();
        amc.configChangeObservers.add(observer);
    }

    private void fillPolicyQuotas() {
        policyQuotas.clear();
        for(Policy policy : amc.getPolicies().values()){
            String name = policy.getName();
            long quotaSize = policy.getQuota().getSize();
            int interval = policy.getQuota().getInterval();
            HashSet<String> services = new HashSet<String>(policy.getServiceProxies());
            PolicyQuota pq = new PolicyQuota();
            pq.setName(name);
            pq.setSize(quotaSize);
            pq.setInterval(Duration.standardSeconds(interval));
            pq.incrementNextCleanup();
            pq.setServices(services);
            policyQuotas.put(name,pq);
        }
    }



    public Outcome handleRequest(Exchange exc){
        return handle(exc, exc.getRequest());
    }

    public Outcome handleResponse(Exchange exc){
        return handle(exc, exc.getResponse());
    }

    private Outcome handle(Exchange exc, Message msg){
        Object apiKeyObj = exc.getProperty(Exchange.API_KEY);
        if(apiKeyObj == null){
            log.warn("No api key set in exchange");
            return Outcome.RETURN;
        }
        String apiKey = (String) apiKeyObj;
        String requestedService = exc.getRule().getName();
        QuotaReachedAnswer answer = isQuotaReached(msg,requestedService,apiKey);
        if(msg instanceof Request) { // lets responses over the limit always through
            if (answer.isQuotaReached()) {
                setResponseToServiceUnavailable(exc, answer.getPq());
                return Outcome.RETURN;
            }
        }
        return Outcome.CONTINUE;
    }

    private void setResponseToServiceUnavailable(Exchange exc, PolicyQuota pq) {
        //TODO do a better response here

        Header hd = new Header();


        DateTimeFormatter dtFormatter = DateTimeFormat.forPattern("HH:mm:ss aa");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        JsonGenerator jgen = null;
        try {
            jgen = new JsonFactory().createGenerator(os);
            jgen.writeStartObject();
            jgen.writeObjectField("Statuscode", 429);
            jgen.writeObjectField("Message", "Quota Exceeded");
            jgen.writeEndObject();
            jgen.close();
        } catch (IOException ignored) {
        }

        Response resp = Response.ResponseBuilder.newInstance().status(429, "Too Many Requests.")
                .header(hd).contentType("application/json").body(os.toByteArray()).build();
        exc.setResponse(resp);
    }

    private QuotaReachedAnswer isQuotaReached(Message msg, String requestedService, String apiKey) {
        doCleanup();
        long size = msg.getHeader().toString().getBytes().length + msg.getHeader().getContentLength();
        addRequestEntry(apiKey, size);
        ApiKeyByteCounter info = keyByteCounter.get(apiKey);
        boolean resultTemp = false;
        PolicyQuota pqTemp = null;
        synchronized (info){
            for (String policy : info.getPolicyByteCounters().keySet()) {
                PolicyQuota pq = policyQuotas.get(policy);
                if (!pq.getServices().contains(requestedService)) {
                    // the service is not in this policy
                    //System.out.println("service not found in " + policy);
                    continue;
                }
                if (info.getPolicyByteCounters().get(policy).get() > pq.getSize()) {
                    resultTemp = true;
                    pqTemp = pq;
                    //System.out.println("limit reached for " + policy);
                    continue;
                }
                // if atleast one policy has available quota, then let it through
                resultTemp = false;
                //System.out.println("limit not reached for " + policy);
                break;
            }
        }
        if(resultTemp){
            return QuotaReachedAnswer.createQuotaReached(pqTemp);
        }else{
            return QuotaReachedAnswer.createQuotaNotReached();
        }
    }

    private void addRequestEntry(String apiKey, long sizeOfBytes) {
        synchronized (keyByteCounter) {
            if (!keyByteCounter.containsKey(apiKey)) {
                ApiKeyByteCounter value = new ApiKeyByteCounter();
                Key key = amc.getKeys().get(apiKey);
                for(Policy p : key.getPolicies()){
                    value.getPolicyByteCounters().put(p.getName(), new AtomicLong());
                }
                keyByteCounter.put(apiKey, value);
            }
        }

        ApiKeyByteCounter keyInfo = keyByteCounter.get(apiKey);
        for(AtomicLong counter : keyInfo.getPolicyByteCounters().values()) {
            counter.addAndGet(sizeOfBytes);
        }
    }

    private void doCleanup() {
        synchronized (policyQuotas) {
            for (PolicyQuota pq : policyQuotas.values()) {
                if (DateTime.now().isAfter(pq.getNextCleanup())) {
                    for(ApiKeyByteCounter keyInfo : keyByteCounter.values()){
                        if(keyInfo.getPolicyByteCounters().keySet().contains(pq.getName())){
                            keyInfo.getPolicyByteCounters().get(pq.getName()).set(0);
                        }
                    }
                    pq.incrementNextCleanup();
                }
            }
        }
    }


}
