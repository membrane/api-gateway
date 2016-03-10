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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import org.springframework.beans.factory.annotation.Required;

import java.util.*;


@MCElement(name="claims")
public class ClaimList {
    public HashSet<String> getSupportedClaims() {
        return supportedClaims;
    }

    public void setSupportedClaims(HashSet<String> supportedClaims) {
        this.supportedClaims = supportedClaims;
    }

    @MCElement(name="scope", topLevel=false, id="claims-scope")
    public static class Scope{
        private String id;
        private String claims;

        public Scope(){
        }

        public Scope(String id, String claims){
            this.id = id;
            this.claims = claims;
        }

        public String getId() {
            return id;
        }

        /**
         * @description name of the scope
         */
        @Required
        @MCAttribute
        public void setId(String id) {
            this.id = id;
        }

        public String getClaims() {
            return claims;
        }

        /**
         * @description the properties seperated by spaces
         */
        @Required
        @MCAttribute
        public void setClaims(String claims) {
            this.claims = claims;
        }
    }

    private List<Scope> scopes = new ArrayList<Scope>();
    HashMap<String,HashSet<String>> scopesToClaims = new HashMap<String, HashSet<String>>();

    private String value;
    private HashSet<String> supportedClaims = new HashSet<String>();

    public void init(Router router){
        setScopes(scopes);
    }

    public HashSet<String> getValidClaims(String claimsToCheck){
        HashSet<String> result = new HashSet<String>();
        String[] split = claimsToCheck.split(" ");
        for(String providedClaim : split)
            if(getSupportedClaims().contains(providedClaim))
                result.add(providedClaim);
        return result;
    }

    public Map<String,String> getClaimsFromSession(Map<String, String> sessionProperties, HashSet<String> claims) {
        HashMap<String,String> result = new HashMap<String, String>();
        for(String claim : claims)
            result.put(claim,sessionProperties.get(claim));
        return result;
    }

    public HashSet<String> getClaimsForScope(String scope) {
        return scopesToClaims.get(scope);
    }

    private String hashSetToString(HashSet<String> set){
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for(String element : set){
            if(first) {
                first = false;
                builder.append(element);
            } else
                builder.append(" ").append(element);
        }
        return builder.toString();
    }

    public boolean scopeExists(String s) {
        return scopesToClaims.containsKey(s);
    }

    public String getValue() {
        return value;
    }

    @Required
    @MCAttribute
    public void setValue(String value) {
        this.value = value;
    }

    public List<Scope> getScopes() {
        return scopes;
    }

    @MCChildElement(order = 1)
    public void setScopes(List<Scope> scopes) {
        createSupportedClaims();
        addOpenidScope(scopes);
        scopesToClaims.clear();
        for(Scope scope : scopes) {
            scopesToClaims.put(scope.id, getValidClaims(scope.getClaims()));
            scope.setClaims(hashSetToString(getValidClaims(scope.getClaims())));
        }
        this.scopes = scopes;
    }

    private void addOpenidScope(List<Scope> scopes) {
        for(Scope scope : scopes){
            if(scope.id.equals("openid"))
                return;
        }
        scopes.add(new Scope("openid",""));
    }

    private void createSupportedClaims() {
        setSupportedClaims(new HashSet<String>(Arrays.asList(value.split(" "))));
    }
}
