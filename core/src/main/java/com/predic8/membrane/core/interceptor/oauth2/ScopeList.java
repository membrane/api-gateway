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

@MCElement(name="scopes")
public class ScopeList {


    @MCElement(name="scope", topLevel=false, id="scopes-scope")
    public static class Scope{
        private String id;
        private String properties;

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

        public String getProperties() {
            return properties;
        }

        /**
         * @description the properties seperated by spaces
         */
        @Required
        @MCAttribute
        public void setProperties(String properties) {
            this.properties = properties;
        }
    }

    private List<Scope> scopes = new ArrayList<Scope>();
    HashMap<String,String[]> scopesToProperties = new HashMap<String, String[]>();

    public void init(Router router){
        setScopes(scopes);
    }

    public boolean scopeExists(String scope) {
        return scopesToProperties.containsKey(scope);
    }

    public Map<String,String> getScopes(Map<String,String> sourceAttributes, String... scopeNames){
        HashMap<String,String> params = new HashMap<String, String>();
        for(String scopename : scopeNames){
            if(!scopesToProperties.containsKey(scopename))
                continue;
            String[] properties = scopesToProperties.get(scopename);
            for(String property : properties){
                if(!sourceAttributes.containsKey(property))
                    continue; // silent failing: If property doesnt exist, just go to next
                params.put(property,sourceAttributes.get(property));
            }
        }
        return params;
    }

    public List<Scope> getScopes() {
        return scopes;
    }

    @MCChildElement(order = 1)
    public void setScopes(List<Scope> scopes) {
        scopesToProperties.clear();
        for(Scope scope : scopes){
            String[] properties = scope.properties.split(" ");
            scopesToProperties.put(scope.id,properties);
        }
        this.scopes = scopes;
    }

}
