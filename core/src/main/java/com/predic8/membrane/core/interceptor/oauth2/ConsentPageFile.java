/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.resolver.ResolverMap;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConsentPageFile {

    public static final String SCOPE_DESCRIPTIONS = "scope_descriptions";
    public static final String CLAIM_DESCRIPTIONS = "claim_descriptions";

    public static final String PRODUCT_NAME = "product_name";
    public static final String LOGO_URL = "logo_url";
    public static final String SCOPES = "scopes";
    public static final String CLAIMS = "claims";
    private ResolverMap resolver;

    String productName;
    String logoUrl;
    HashMap<String,String> scopesToDescriptions = new HashMap<String, String>();
    HashMap<String,String> claimsToDescriptions = new HashMap<String, String>();
    private Map<String, Object> json;


    public void init(Router router, String url) throws IOException {
        resolver = router.getResolverMap();
        if(url == null) {
            createDefaults();
            return;
        }
        parseFile(getFromUrl(url));
    }

    private void parseFile(String consentPageFile) throws IOException {
        parseJson(consentPageFile);
        parseProductAndLogo();
        parseScopes();
        parseClaims();
    }

    private void parseJson(String consentPageFile) throws IOException {
        json = new ObjectMapper().readValue(consentPageFile, Map.class);
    }

    private void parseProductAndLogo() {
        productName = (String) json.get(PRODUCT_NAME);
        logoUrl = (String) json.get(LOGO_URL);
    }

    private void parseClaims() {
        Map<String,Object> claims = (Map<String, Object>) json.get(CLAIMS);
        for(String claim : claims.keySet())
            claimsToDescriptions.put(claim, (String) claims.get(claim));
    }

    private void parseScopes() {
        Map<String,Object> scopes = (Map<String, Object>) json.get(SCOPES);
        for(String scope : scopes.keySet())
            scopesToDescriptions.put(scope, (String) scopes.get(scope));
    }

    private String getFromUrl(String url) throws IOException {
        return IOUtils.toString(resolver.resolve(url));
    }

    private void createDefaults() {
    }

    public String convertScope(String scope){
        if(!scopesToDescriptions.containsKey(scope))
            return scope;
        return scopesToDescriptions.get(scope);
    }

    public String convertClaim(String claim){
        if(!claimsToDescriptions.containsKey(claim))
            return claim;
        return claimsToDescriptions.get(claim);
    }
}
