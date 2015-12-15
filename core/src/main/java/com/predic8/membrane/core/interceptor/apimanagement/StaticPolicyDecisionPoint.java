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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.apimanagement.policy.Policy;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;

import java.io.InputStream;

@MCElement(name="staticPolicyDecisionPoint")
public class StaticPolicyDecisionPoint {

    private ApiManagementConfiguration configuration;
    String location;

    public String getLocation() {
        return location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public void init(Router router) throws ResourceRetrievalException {
        InputStream is = router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), location));
        setConfiguration(new ApiManagementConfiguration(is));

    }

    public AuthorizationResult getAuthorization(Exchange exc, String apiKey) {
        if(!getConfiguration().getKeys().containsKey(apiKey))
        {
            return AuthorizationResult.getAuthorizedFalse("API Key not found");
        }
        Key key = getConfiguration().getKeys().get(apiKey);
        String requestedAPI = exc.getRule().getName();

        String reasonFailed ="";
        for(Policy policy : key.getPolicies()){
            if(policy.getServiceProxies().contains(requestedAPI)){
                if(isAccessGranted(key)) {
                    configureExchangeToPointToTarget(exc);
                    return AuthorizationResult.getAuthorizedTrue();
                }
                reasonFailed = "Access denied";
            }
        }
        reasonFailed = "Service not found";
        return AuthorizationResult.getAuthorizedFalse(reasonFailed);
    }

    private boolean isAccessGranted(Key key) {
        // TODO
        return true;
    }

    private void configureExchangeToPointToTarget(Exchange exc) {
    }


    public ApiManagementConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ApiManagementConfiguration configuration) {
        this.configuration = configuration;
    }
}
