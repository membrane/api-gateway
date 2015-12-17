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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apimanagement.ApiManagementConfiguration;
import com.predic8.membrane.core.interceptor.apimanagement.Key;
import com.predic8.membrane.core.interceptor.apimanagement.policy.Policy;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unfinished
 */
@MCElement(name="amQuota")
public class AMQuota {

    private static Logger log = LogManager.getLogger(AMQuota.class);
    private ApiManagementConfiguration amc;

    public ConcurrentHashMap<String, ApiKeyByteCounter> keyByteCounter = new ConcurrentHashMap<String, ApiKeyByteCounter>();
    public ConcurrentHashMap<String, PolicyQuota> policyQuotas = new ConcurrentHashMap<String, PolicyQuota>();

    public ApiManagementConfiguration getAmc() {
        return amc;
    }

    public void setAmc(ApiManagementConfiguration amc) {
        this.amc = amc;
        fillPolicyCleanupTimes();
    }

    private void fillPolicyCleanupTimes() {
        policyQuotas.clear();
        for(Policy policy : amc.getPolicies().values()){
            String name = policy.getName();
            long quotaSize = policy.getQuota().getSize();
            int interval = policy.getQuota().getInterval();
            HashSet<String> services = new HashSet<String>(policy.getServiceProxies());
            PolicyQuota pq = new PolicyQuota();
            pq.setName(name);
            pq.setSize(quotaSize);
            pq.setInterval(interval);
            pq.setServices(services);
            policyQuotas.put(name,pq);
        }
    }



    public Outcome handleRequest(Exchange exc){
        //System.out.println("amQuotaReq");
        //return handle(exc, exc.getRequest());
        return Outcome.CONTINUE;
    }

    public Outcome handleResponse(Exchange exc){
        //System.out.println("amQuotaResp");
        //return handle(exc, exc.getResponse());
        return Outcome.CONTINUE;
    }

    private Outcome handle(Exchange exc, Message msg){
        Object apiKeyObj = exc.getProperty(Exchange.API_KEY);
        if(apiKeyObj == null){
            log.warn("No api key set in exchange");
            return Outcome.RETURN;
        }
        String apiKey = (String) apiKeyObj;
        String requestedService = exc.getRule().getName();
        QuotaReachedAnswer answer = isQuotaReached(exc,requestedService,apiKey);
        if (answer.isQuotaReached()) {
            setResponseToServiceUnavailable(exc,answer.getPq());
            return Outcome.RETURN;
        }
        return Outcome.CONTINUE;
    }

    private void setResponseToServiceUnavailable(Exchange exc, PolicyQuota pq) {
        //TODO
    }

    private QuotaReachedAnswer isQuotaReached(Exchange exc, String requestedService, String apiKey) {
        doCleanup();
        long size = getSizeFromExchange(exc);
        addRequestEntry(apiKey, size);


        //TODO
        return null;

    }

    private long getSizeFromExchange(Exchange exc) {
        //exc.

        // TODO
        return 0;
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
        //TODO
    }


}
