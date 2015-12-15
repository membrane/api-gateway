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

public class StaticPolicyDecisionPoint {

    private ApiManagementConfiguration configuration;
    /*String location;

    public String getLocation() {
        return location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }*/

    /*public void init(Router router) throws ResourceRetrievalException {
        InputStream is = router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), location));
        setConfiguration(new ApiManagementConfiguration(is));
        setConfiguration();
    }*/

    public StaticPolicyDecisionPoint(ApiManagementConfiguration amc){
        this.configuration = amc;
    }

    public AuthorizationResult getAuthorization(Exchange exc, String apiKey) {
        if(!getConfiguration().getKeys().containsKey(apiKey))
        {
            return AuthorizationResult.getAuthorizedFalse("API key not found");
        }
        Key key = getConfiguration().getKeys().get(apiKey);
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

    public ApiManagementConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ApiManagementConfiguration configuration) {
        this.configuration = configuration;
    }
}
