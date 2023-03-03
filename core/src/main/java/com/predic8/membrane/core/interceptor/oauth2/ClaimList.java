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
import com.predic8.membrane.core.util.functionalInterfaces.Function;
import com.predic8.membrane.annot.Required;

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

    private List<Scope> scopes = new ArrayList<>();
    HashMap<String,HashSet<String>> scopesToClaims = new HashMap<>();

    private String value;
    private HashSet<String> supportedClaims = new HashSet<>();

    public void init(Router router){
        setScopes(scopes);
    }

    public String getSupportedScopes(){
        return toString(scopes);
    }

    public String getSupportedClaimsAsString(){
        return toString(supportedClaims);
    }

    public HashSet<String> getValidClaims(String claimsToCheck){
        HashSet<String> result = new HashSet<>();
        String[] split = claimsToCheck.split(" ");
        for(String providedClaim : split)
            if(getSupportedClaims().contains(providedClaim))
                result.add(providedClaim);
        return result;
    }

    public Map<String,String> getClaimsFromSession(Map<String, String> sessionProperties, HashSet<String> claims) {
        HashMap<String,String> result = new HashMap<>();
        for(String claim : claims)
            result.put(claim,sessionProperties.get(claim));
        return result;
    }

    public HashSet<String> getClaimsForScope(String scope) {
        return scopesToClaims.get(scope);
    }

    /**
     * takes any iterable and converts it to a string by calling fun on every element
     */
    private <T> String toString(Iterable<T> iterable, Function<T,String> fun){
        StringBuilder builder = new StringBuilder();
        for(T element : iterable)
            builder.append(" ").append(fun.call(element));
        return builder.toString().trim();
    }

    private <T> String toString(Iterable<T> iterable){
        return toString(iterable, new Function<>() {
            @Override
            public String call(T param) {
                return param.toString();
            }
        });
    }

    private String toString(List<Scope> scopes){
        return toString(scopes, new Function<>() {
            @Override
            public String call(Scope param) {
                return param.getId();
            }
        });
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
            scope.setClaims(toString(getValidClaims(scope.getClaims())));
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
        setSupportedClaims(new HashSet<>(Arrays.asList(value.split(" "))));
    }
}
