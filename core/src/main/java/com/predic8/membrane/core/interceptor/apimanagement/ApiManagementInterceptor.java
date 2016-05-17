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
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.interceptor.apimanagement.apiconfig.ApiConfig;
import com.predic8.membrane.core.interceptor.apimanagement.apiconfig.EtcdRegistryApiConfig;
import com.predic8.membrane.core.interceptor.apimanagement.apiconfig.SimpleApiConfig;
import com.predic8.membrane.core.interceptor.apimanagement.policy.Policy;
import com.predic8.membrane.core.interceptor.apimanagement.quota.AMQuota;
import com.predic8.membrane.core.interceptor.apimanagement.rateLimiter.AMRateLimiter;
import com.predic8.membrane.core.interceptor.apimanagement.statistics.AMStatisticsCollector;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.Rule;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

@MCElement(name = "apiManagement")
public class ApiManagementInterceptor extends AbstractInterceptor {

    public static final String APPLICATION_JSON = "application/json"; // @TODO use constant somewhere else
    private static Logger log = LogManager.getLogger(ApiManagementInterceptor.class);
    private ApiConfig apiConfig;
    private AMRateLimiter amRateLimiter = null;
    private AMQuota amQuota = null;
    private AMStatisticsCollector amStatisticsCollector;
    private ApiManagementConfiguration apiManagementConfiguration = null;
    private String config = "api.yaml";
    private ApiKeyRetriever apiKeyRetriever = new HeaderKeyRetriever();

    @Override
    public void init(Router router) throws Exception {
        super.init(router);

        Map<String, ApiConfig> apiConfigs = router.getBeanFactory().getBeansOfType(ApiConfig.class);
        String etcdRegistryApiConfigCorrected = getCorrectedName(EtcdRegistryApiConfig.class.getSimpleName());
        String simpleApiConfigCorrected = getCorrectedName(SimpleApiConfig.class.getSimpleName());

        if (apiConfigs.containsKey(etcdRegistryApiConfigCorrected)) {
            apiConfig = apiConfigs.get(etcdRegistryApiConfigCorrected);
        } else if (apiConfigs.containsKey(simpleApiConfigCorrected)) {
            apiConfig = apiConfigs.get(simpleApiConfigCorrected);
        }
        if (apiConfig != null) {
            log.info("used apiConfig: " + apiConfig.getClass().getSimpleName());
            apiManagementConfiguration = apiConfig.getConfiguration();
        } else {
            log.info("No ApiConfig set. Using default");
            apiManagementConfiguration = new ApiManagementConfiguration(router.getBaseLocation(), config);
        }

        addInterceptors();
    }

    private void addInterceptors() {
        StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append("Api Management Interceptor");
        ArrayList<String> interceptors = new ArrayList<String>();
        nameBuilder.append(" { ");
        if (amRateLimiter != null) {
            amRateLimiter.setAmc(apiManagementConfiguration);
            interceptors.add("RateLimiter");
        }
        if (amQuota != null) {
            amQuota.setAmc(apiManagementConfiguration);
            interceptors.add("Quota");
        }
        //temp
        interceptors.add("Statistics");

        if (interceptors.size() > 0) {
            nameBuilder.append(interceptors.get(0));
            for (int i = 1; i < interceptors.size(); i++) {
                nameBuilder.append(", ").append(interceptors.get(i));
            }
        }
        nameBuilder.append(" }");
        this.name = nameBuilder.toString();
    }

    private String getCorrectedName(String etcdRegistryApiConfig) {
        return etcdRegistryApiConfig.substring(0, 1).toLowerCase() + etcdRegistryApiConfig.substring(1);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if(getAmStatisticsCollector() != null)
            return getAmStatisticsCollector().handleRequest(exc,handleRequest2(exc) );
        return handleRequest2(exc);
    }

    private Outcome handleRequest2(Exchange exc) throws Exception {
        String key = getAndRemoveKey(exc);
        if (!keyExists(key) || isAdminConsoleCall(exc)) {
            return processUnauthorizedRequest(exc);
        }
        exc.setProperty(Exchange.API_KEY, key);

        AuthorizationResult auth = getAuthorization(exc, key);
        if (auth.isAuthorized()) {
            return processAuthorizedRequest(exc);
        }
        setResponsePolicyDenied(exc, auth);
        return Outcome.RETURN;
    }

    private boolean keyExists(String key) {
        return apiManagementConfiguration.getKeys().containsKey(key);
    }

    private Outcome processAuthorizedRequest(Exchange exc) throws Exception {
        if (getAmRateLimiter() != null && getAmRateLimiter().handleRequest(exc) == Outcome.RETURN) {
            return Outcome.RETURN;
        }
        if (getAmQuota() != null && getAmQuota().handleRequest(exc) == Outcome.RETURN) {
            return Outcome.RETURN;
        }
        return Outcome.CONTINUE;
    }

    private Outcome processUnauthorizedRequest(Exchange exc) {
        exc.setProperty(Exchange.API_KEY, ApiManagementConfiguration.UNAUTHORIZED_API_KEY);

        if(hasUnauthorizedPolicy(exc) || isAdminConsoleCall(exc))
            return Outcome.CONTINUE;
        setResponseNoAuthKey(exc);
        return Outcome.RETURN;
    }

    private String getAndRemoveKey(Exchange exc) {
        String key = apiKeyRetriever.getKey(exc);
        apiKeyRetriever.removeKey(exc);
        return key;
    }

    private boolean isAdminConsoleCall(Exchange exc) {
        for (Rule r : router.getRuleManager().getRules()) {
            if (!(r instanceof AbstractServiceProxy)) continue;

            for (Interceptor i : r.getInterceptors()) {
                if (i instanceof AdminConsoleInterceptor) {
                    for(Interceptor interceptor : exc.getRule().getInterceptors()){
                        if(interceptor == i)
                            return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        if(getAmStatisticsCollector() != null)
            return getAmStatisticsCollector().handleResponse(exc, handleResponse2(exc));
        return handleResponse2(exc);
    }

    private Outcome handleResponse2(Exchange exc) {
        if (!(hasUnauthorizedPolicy(exc) || isAdminConsoleCall(exc)) && getAmQuota() != null) {
            getAmQuota().handleResponse(exc);
        }
        return Outcome.CONTINUE;
    }

    private boolean hasUnauthorizedPolicy(Exchange exc) {
        String requestedServiceProxy = exc.getRule().getName();
        for(Policy pol : apiManagementConfiguration.getPolicies().values())
            if(pol.getServiceProxies().contains(requestedServiceProxy) && pol.isUnauthenticated())
                return true;
        return false;
    }

    public AuthorizationResult getAuthorization(Exchange exc, String apiKey) {
        if (!keyExists(apiKey))
            return AuthorizationResult.getAuthorizedFalse("API key not found");
        if(keyIsExpired(apiKey))
            return AuthorizationResult.getAuthorizedFalse("API key expired");

        for (Policy policy : getKey(apiKey).getPolicies()) {
            if (policy.getServiceProxies().contains(exc.getRule().getName())) {
                return AuthorizationResult.getAuthorizedTrue();
            }
        }
        return AuthorizationResult.getAuthorizedFalse("Service not available: " + exc.getRule().getName());
    }

    private boolean keyIsExpired(String apiKey) {
        if(getKey(apiKey).getExpiration() == null)
            return false;
        if(getKey(apiKey).getExpiration().isBefore(LocalDateTime.now()))
            return true;
        return false;
    }

    private Key getKey(String apiKey) {
        return apiManagementConfiguration.getKeys().get(apiKey);
    }

    private byte[] buildJsonErrorMessage(Response res) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        JsonGenerator jgen;
        try {
            jgen = new JsonFactory().createGenerator(os);
            jgen.writeStartObject();
            jgen.writeObjectField("Statuscode", res.getStatusCode());
            jgen.writeObjectField("Message", res.getStatusMessage());
            jgen.writeEndObject();
            jgen.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return os.toByteArray();
    }

    private void setResponsePolicyDenied(Exchange exc, AuthorizationResult auth) {
        setErrorResponse(exc, Response.badRequest(auth.getReason()));
    }

    private void setResponseNoAuthKey(Exchange exc) {
        setErrorResponse(exc, Response.unauthorized());
    }

    private void setErrorResponse(Exchange exc, Response.ResponseBuilder builder) {
        Response res = builder.contentType(APPLICATION_JSON).build();
        res.setBodyContent(buildJsonErrorMessage(res));
        exc.setResponse(res);
    }

    public AMRateLimiter getAmRateLimiter() {
        return amRateLimiter;
    }

    @MCChildElement(order = 0)
    public void setAmRateLimiter(AMRateLimiter amRateLimiter) {
        this.amRateLimiter = amRateLimiter;
    }

    public AMQuota getAmQuota() {
        return amQuota;
    }

    @MCChildElement(order = 1)
    public void setAmQuota(AMQuota amQuota) {
        this.amQuota = amQuota;
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
    }

    public AMStatisticsCollector getAmStatisticsCollector() {
        return amStatisticsCollector;
    }

    @MCChildElement(order = 2)
    public void setAmStatisticsCollector(AMStatisticsCollector amStatisticsCollector) {
        this.amStatisticsCollector = amStatisticsCollector;
    }
}
