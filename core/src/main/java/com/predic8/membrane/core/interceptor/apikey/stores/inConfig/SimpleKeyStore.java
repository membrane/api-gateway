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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.apikey.stores.*;

import java.util.*;

import static java.util.Optional.*;
import static java.util.stream.Collectors.*;

/**
 * @description Stores api keys inline as XML.
 */
@MCElement(name = "keys", topLevel = false)
public class SimpleKeyStore implements ApiKeyStore {

    private final List<Key> keys = new ArrayList<>();

    /**
     * @description <secret value="..." /> elements containing the key as value property.
     */
    @MCChildElement(allowForeign = true)
    public void setKeys(List<Key> keys) {
        this.keys.addAll(keys);
    }

    public List<Key> getKeys() {
        return keys;
    }


    @Override
    public void init(Router router) {
    }

    @Override
    public Optional<Set<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException {
        var key = keys.stream().filter(k -> k.getValue().equals(apiKey)).findFirst();
        if (key.isPresent()) {
            Set<String> scopeValues = key.get().getScopes().stream()
                    .map(Scope::getValue)
                    .collect(toSet());
            return ofNullable(scopeValues.isEmpty() ? null : scopeValues);
        } else {
            throw new UnauthorizedApiKeyException();
        }
    }
}