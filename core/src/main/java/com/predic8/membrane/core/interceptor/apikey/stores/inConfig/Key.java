/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.apikey.stores.inConfig;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @description Contains api keys and scopes.
 */
@MCElement(name = "secret", topLevel = false)
public class Key {

    private final List<Scope> scopes = new ArrayList<>();

    private String value;

    /**
     * @description <scope>...</scope> elements for defining scopes for this key.
     */
    @MCChildElement(allowForeign = true)
    public void setScopes(List<Scope> scopes) {
        this.scopes.addAll(scopes);
    }

    public List<Scope> getScopes() {
        return scopes;
    }

    /**
     * @description The api key itself.
     */
    @MCAttribute
    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
