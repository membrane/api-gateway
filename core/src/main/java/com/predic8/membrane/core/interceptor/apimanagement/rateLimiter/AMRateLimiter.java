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

package com.predic8.membrane.core.interceptor.apimanagement.rateLimiter;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
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
import org.joda.time.format.PeriodFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.predic8.membrane.core.http.MimeType.*;

@MCElement(name="amRateLimiter")
public class AMRateLimiter {

    private static Logger log = LoggerFactory.getLogger(AMRateLimiter.class);
    private ApiManagementConfiguration amc;

    public ConcurrentHashMap<String, ApiKeyRequestCounter> keyInformation = new ConcurrentHashMap<String, ApiKeyRequestCounter>();
    public ConcurrentHashMap<String, PolicyRateLimit> policyRateLimits = new ConcurrentHashMap<String, PolicyRateLimit>();

    public ApiManagementConfiguration getAmc() {
        return amc;
    }

    private Runnable observer = new Runnable() {
        @Override
        public void run() {
            log.info("Getting new config");
            keyInformation = new ConcurrentHashMap<>();
            fillPolicyCleanupTimes();
        }
    };

    public void setAmc(ApiManagementConfiguration amc) {
        if(this.amc != null) {
            this.amc.configChangeObservers.remove(observer);
        }
        this.amc = amc;
        fillPolicyCleanupTimes();
        amc.configChangeObservers.add(observer);
    }

    private void fillPolicyCleanupTimes() {
        policyRateLimits.clear();
        for(Policy policy : amc.getPolicies().values()){
            String name = policy.getName();
            int requests = policy.getRateLimit().getRequests();
            Duration interval = Duration.standardSeconds(policy.getRateLimit().getInterval());
            HashSet<String> services = new HashSet<String>(policy.getServiceProxies());
            PolicyRateLimit prl = new PolicyRateLimit();
            prl.setName(name);
            prl.setRequests(requests);
            prl.setInterval(interval);
            prl.setServices(services);
            prl.incrementNextCleanup();
            policyRateLimits.put(name,prl);
        }
    }

    public Outcome handleRequest(Exchange exc) throws Exception {
        Object keyObj = exc.getProperty(Exchange.API_KEY);
        if(keyObj == null){
            log.warn("No api key set in exchange");
            return Outcome.RETURN;
        }
        String apiKey = (String) keyObj;
        String service = exc.getRule().getName();
        LimitReachedAnswer answer = isRequestLimitReached(service,apiKey);
        if (answer.isLimitReached()) {
            setResponseToServiceUnavailable(exc,answer.getPrl());
            return Outcome.RETURN;
        }
        return Outcome.CONTINUE;

    }

    public void setResponseToServiceUnavailable(Exchange exc, PolicyRateLimit prl) throws UnsupportedEncodingException {
        Header hd = new Header();
        DateTimeFormatter dateFormatter = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZoneUTC()
                .withLocale(Locale.US);
        hd.add("Date", dateFormatter.print(DateTime.now()));
        hd.add("X-LimitDuration", PeriodFormat.getDefault().print(prl.getInterval().toPeriod()));
        hd.add("X-LimitRequests", Integer.toString(prl.getRequests()));
        String ip = exc.getRemoteAddrIp();
        DateTime availableAgainDateTime = prl.getNextCleanup();
        hd.add("X-LimitReset", Long.toString(availableAgainDateTime.getMillis()));

        /*StringBuilder bodyString = new StringBuilder();
        DateTimeFormatter dtFormatter = DateTimeFormat.forPattern("HH:mm:ss aa");
        bodyString.append(ip).append(" exceeded the rate limit of ").append(prl.getRequests())
                .append(" requests per ")
                .append(PeriodFormat.getDefault().print(prl.getInterval().toPeriod()))
                .append(". The next request can be made at ").append(dtFormatter.print(availableAgainDateTime));*/


        DateTimeFormatter dtFormatter = DateTimeFormat.forPattern("HH:mm:ss aa");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        JsonGenerator jgen = null;
        try {
            jgen = new JsonFactory().createGenerator(os);
            jgen.writeStartObject();
            jgen.writeObjectField("Statuscode", 429);
            jgen.writeObjectField("Message", "The rate limit of " + prl.getRequests() + " requests in " + prl.getInterval().getStandardSeconds()+ " seconds is exceeded. The next requests can be made at "+ dtFormatter.print(availableAgainDateTime));
            jgen.writeEndObject();
            jgen.close();
        } catch (IOException ignored) {
        }

        Response resp = Response.ResponseBuilder.newInstance().status(429, "Too Many Requests.")
                .header(hd).contentType(APPLICATION_JSON).body(os.toByteArray()).build();
        exc.setResponse(resp);
    }

    public LimitReachedAnswer isRequestLimitReached(String service, String apiKey) {
        doCleanup();
        addRequestEntry(apiKey);
        ApiKeyRequestCounter info = keyInformation.get(apiKey);
        boolean resultTemp = false;
        PolicyRateLimit prlTemp = null;
        synchronized(info) {
            for (String policy : info.getPolicyCounters().keySet()) {
                PolicyRateLimit prl = policyRateLimits.get(policy);
                if (!prl.getServices().contains(service)) {
                    // the service is not in this policy
                    //System.out.println("service not found in " + policy);
                    continue;
                }
                if (info.getPolicyCounters().get(policy).get() > prl.getRequests()) {
                    resultTemp = true;
                    prlTemp = prl;
                    //System.out.println("limit reached for " + policy);
                    continue;
                }
                // if atleast one policy has available requests, then let it through
                resultTemp = false;
                //System.out.println("limit not reached for " + policy);
                break;
            }
        }

        if(resultTemp){
            return LimitReachedAnswer.createLimitReached(prlTemp);
        }else{
            return LimitReachedAnswer.createLimitNotReached();
        }
    }

    private void doCleanup(){
        synchronized (policyRateLimits) {
            for (PolicyRateLimit prl : policyRateLimits.values()) {
                if (DateTime.now().isAfter(prl.getNextCleanup())) {
                    for(ApiKeyRequestCounter keyInfo : keyInformation.values()){
                        if(keyInfo.getPolicyCounters().keySet().contains(prl.getName())){
                            keyInfo.getPolicyCounters().get(prl.getName()).set(0);
                        }
                    }
                    prl.incrementNextCleanup();
                }
            }
        }
    }

    private void addRequestEntry(String apiKey) {
        synchronized (keyInformation) {
            if (!keyInformation.containsKey(apiKey)) {
                ApiKeyRequestCounter value = new ApiKeyRequestCounter();
                Key key = amc.getKeys().get(apiKey);
                for(Policy p : key.getPolicies()){
                    value.getPolicyCounters().put(p.getName(), new AtomicInteger());
                }
                keyInformation.put(apiKey, value);
            }
        }

        ApiKeyRequestCounter keyInfo = keyInformation.get(apiKey);
        for(AtomicInteger counter : keyInfo.getPolicyCounters().values()) {
            counter.incrementAndGet();
        }
    }
}
