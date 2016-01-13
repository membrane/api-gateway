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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apimanagement.apiconfig.ApiConfig;
import com.predic8.membrane.core.interceptor.apimanagement.apiconfig.EtcdRegistryApiConfig;
import com.predic8.membrane.core.interceptor.apimanagement.apiconfig.SimpleApiConfig;
import com.predic8.membrane.core.interceptor.apimanagement.policy.Policy;
import com.predic8.membrane.core.interceptor.apimanagement.quota.AMQuota;
import com.predic8.membrane.core.interceptor.apimanagement.rateLimiter.AMRateLimiter;
import com.predic8.membrane.core.interceptor.apimanagement.statistics.AMStatisticsCollector;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

@MCElement(name = "apiManagement")
public class ApiManagementInterceptor extends AbstractInterceptor {

    private static Logger log = LogManager.getLogger(ApiManagementInterceptor.class);
    private ApiConfig apiConfig;
    private AMRateLimiter amRli = null;
    private AMQuota amQ = null;
    private AMStatisticsCollector amSc = null;
    private ApiManagementConfiguration amc = null;
    private String config = "api.yaml";
    private final String UNAUTHORIZED_API_KEY = "UNAUTHORIZED_API_KEY";
    private ApiKeyRetriever apiKeyRetriever = new HeaderKeyRetriever();

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append("Api Management Interceptor");
        Map<String, ApiConfig> apiConfigs = router.getBeanFactory().getBeansOfType(ApiConfig.class);
        String etcdRegistryApiConfig = EtcdRegistryApiConfig.class.getSimpleName();
        String etcdRegistryApiConfigCorrected = etcdRegistryApiConfig.substring(0,1).toLowerCase() + etcdRegistryApiConfig.substring(1);
        String simpleApiConfig = SimpleApiConfig.class.getSimpleName();
        String simpleApiConfigCorrected = simpleApiConfig.substring(0,1).toLowerCase() + simpleApiConfig.substring(1);


        if(apiConfigs.containsKey(etcdRegistryApiConfigCorrected)){
            apiConfig = apiConfigs.get(etcdRegistryApiConfigCorrected);
        }
        else if(apiConfigs.containsKey(simpleApiConfigCorrected)){
            apiConfig = apiConfigs.get(simpleApiConfigCorrected);
        }
        if(apiConfig != null){
            log.info("used apiConfig: " + apiConfig.getClass().getSimpleName());
            amc = apiConfig.getConfiguration();
        }else{
            log.info("No ApiConfig set. Using default");
            amc = new ApiManagementConfiguration(router.getBaseLocation(),"api.yaml");
        }

        ArrayList<String> interceptors = new ArrayList<String>();
        nameBuilder.append(" { ");
        if (amRli != null) {
            amRli.setAmc(amc);
            interceptors.add("RateLimiter");
        }
        if(amQ != null){
            amQ.setAmc(amc);
            interceptors.add("Quota");
        }

        //temp
        amSc = new AMStatisticsCollector();
        interceptors.add("Statistics");

        if(interceptors.size() > 0){
            nameBuilder.append(interceptors.get(0));
            for(int i = 1; i < interceptors.size();i++){
                nameBuilder.append(", ").append(interceptors.get(i));
            }
        }
        nameBuilder.append(" }");
        this.name = nameBuilder.toString();
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if(amSc != null)
            return amSc.handleRequest(exc,handleRequest2(exc));
        return handleRequest2(exc);
    }

    private Outcome handleRequest2(Exchange exc) throws Exception {
        String key = apiKeyRetriever.getKey(exc);

        if (key == null) {
            if (!hasUnauthorizedPolicy(exc)) {
                setResponseNoAuthKey(exc);
                return Outcome.RETURN;
            }
            exc.setProperty(Exchange.API_KEY, UNAUTHORIZED_API_KEY);
            return Outcome.CONTINUE;
        }
        exc.setProperty(Exchange.API_KEY, key);
        AuthorizationResult auth = getAuthorization(exc, key);
        if (auth.isAuthorized()) {
            if (getAmRli() != null) {
                if(getAmRli().handleRequest(exc) == Outcome.RETURN){
                    return Outcome.RETURN;
                }
            }
            if (getAmQ() != null) {
                if(getAmQ().handleRequest(exc) == Outcome.RETURN){
                    return Outcome.RETURN;
                }
            }
            return Outcome.CONTINUE;
        } else {
            setResponsePolicyDenied(exc, auth);
            return Outcome.RETURN;
        }
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        if(amSc != null)
            return amSc.handleResponse(exc,handleResponse2(exc));
        return handleResponse2(exc);
    }

    private Outcome handleResponse2(Exchange exc) {
        if (!hasUnauthorizedPolicy(exc)) {
            if (getAmQ() != null) {
                getAmQ().handleResponse(exc);
            }
        }
        return Outcome.CONTINUE;
    }

    private boolean hasUnauthorizedPolicy(Exchange exc) {
        Policy unauthPol = amc.getPolicies().get("unauthorized");
        if (unauthPol == null) {
            return false;
        }
        String requestedApi = exc.getRule().getName();
        for(String unauthedServices : unauthPol.getServiceProxies()){
            String serviceName = unauthPol.getServiceProxies().iterator().next();
            if(requestedApi.equals(serviceName)){
                return true;
            }
        }

        return false;
    }

    public AuthorizationResult getAuthorization(Exchange exc, String apiKey) {
        if(!amc.getKeys().containsKey(apiKey))
        {
            return AuthorizationResult.getAuthorizedFalse("API key not found");
        }
        Key key = amc.getKeys().get(apiKey);
        String requestedAPI = exc.getRule().getName();

        String reasonFailed ="";
        for(Policy policy : key.getPolicies()){
            if(policy.getServiceProxies().contains(requestedAPI)){
                return AuthorizationResult.getAuthorizedTrue();
            }
        }
        reasonFailed = "Service not available: " + requestedAPI;
        return AuthorizationResult.getAuthorizedFalse(reasonFailed);
    }

    private Response buildResponse(int code, String msg, ByteArrayOutputStream baos) {
        Response resp = new Response.ResponseBuilder().status(code, msg).contentType("application/json").body(baos.toByteArray()).build();
        return resp;
    }

    private ByteArrayOutputStream buildJson(int code, String msg) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        JsonGenerator jgen = null;
        try {
            jgen = new JsonFactory().createGenerator(os);
            jgen.writeStartObject();
            jgen.writeObjectField("Statuscode", code);
            jgen.writeObjectField("Message", msg);
            jgen.writeEndObject();
            jgen.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return os;
    }

    private void setResponsePolicyDenied(Exchange exc, AuthorizationResult auth) {
        int code = 400;
        String msg = "Bad request";
        Response resp = buildResponse(code, msg, buildJson(code, msg));
        exc.setResponse(resp);
    }

    private void setResponseNoAuthKey(Exchange exc) {
        int code = 401;
        String msg = "Unauthorized";
        Response resp = buildResponse(code, msg, buildJson(code, msg));
        exc.setResponse(resp);
    }

    public AMRateLimiter getAmRli() {
        return amRli;
    }

    @MCChildElement(order = 0)
    public void setAmRli(AMRateLimiter amRli) {
        this.amRli = amRli;
    }

    public AMQuota getAmQ() {
        return amQ;
    }

    @MCChildElement(order = 1)
    public void setAmQ(AMQuota amQ) {
        this.amQ = amQ;
    }

    public String getConfig() {
        return config;
    }

    /**
     * @description the location of the configuration
     * @default api.yaml
     */
    @MCAttribute
    public void setConfig(String config) {
        this.config = config;
        if(amc != null){
            amc.setLocation(this.config);
        }
    }
}
