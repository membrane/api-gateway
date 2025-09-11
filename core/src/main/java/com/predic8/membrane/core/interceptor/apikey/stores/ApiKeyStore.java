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
package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.core.Router;

import java.util.List;
import java.util.Optional;

public interface ApiKeyStore {

    void init(Router router);

    /**
     * Validates the API Key and returns the associated scopes. If the API key is not found in the store
     * an UnauthorizedApiKeyException in thrown.
     * @param apiKey
     * @return list of Scopes
     * @throws UnauthorizedApiKeyException Thrown when API key is invalid
     */
    Optional<List<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException;
}
