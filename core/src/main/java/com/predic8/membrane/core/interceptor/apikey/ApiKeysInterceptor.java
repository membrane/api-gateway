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
package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyExtractor;
import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.createProblemDetails;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.util.Map.of;
import static java.util.stream.Stream.ofNullable;

@MCElement(name = "apiKey")
public class ApiKeysInterceptor extends AbstractInterceptor {
    private final Logger log = LoggerFactory.getLogger(ApiKeysInterceptor.class);

    public static final String SCOPES = "membrane-scopes";
    public static final String TYPE_4XX = "/authorization-denied";
    public static final String TITLE_4XX = "Access Denied";
    private final List<ApiKeyStore> stores = new ArrayList<>();
    private final List<ApiKeyExtractor> extractors = new ArrayList<>();
    private boolean required = true;

    @Override
    public void init() {
        stores.addAll(router.getBeanFactory().getBeansOfType(ApiKeyStore.class).values());
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        var key = getKey(exc);

        if (required && key.isEmpty()) {
            problemJsonResponse(exc, 401, TYPE_4XX, TITLE_4XX, "Tried to access apiKey protected resource without key.");
            return RETURN;
        }

        if (key.isPresent()) {
            try {
                addScopes(exc, getScopes(key.get()));
            } catch (UnauthorizedApiKeyException e) {
                if (!required) {return CONTINUE;}
                problemJsonResponse(exc, 403, TYPE_4XX, TITLE_4XX, "The provided API key is invalid.");
                return RETURN;
            }
        }
        return CONTINUE;
    }

    public void addScopes(Exchange exc, List<String> scopes) {
        exc.setProperty(SCOPES, scopes);
    }

    public void problemJsonResponse(Exchange exc, int statusCode, String type, String title, String info) {
        log.warn(info);
        exc.setResponse(createProblemDetails(statusCode, type, title, of("error", info)));
    }

    public List<String> getScopes(String key) throws UnauthorizedApiKeyException {
        Set<String> combinedScopes = new LinkedHashSet<>();
        boolean keyFound = false;

        for (ApiKeyStore store : stores) {
            try {
                store.getScopes(key).ifPresent(combinedScopes::addAll);
                keyFound = true;
            } catch (Exception ignored) {}
        }

        if (!keyFound) {
            throw new UnauthorizedApiKeyException();
        }

        return combinedScopes.stream().toList();
    }

    public Optional<String> getKey(Exchange exc) {
        return extractors.stream()
                         .flatMap(ext -> ofNullable(
                                 ext.extract(exc).orElse(null)
                         ))
                         .findFirst();
    }

    @SuppressWarnings("SameParameterValue")
    @MCAttribute
    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }

    @MCChildElement(allowForeign = true)
    public void setStores(List<ApiKeyStore> stores) {
        this.stores.addAll(stores);
    }

    public List<ApiKeyStore> getStores() {
        return stores;
    }

    @MCChildElement(allowForeign = true, order = 1)
    public void setExtractors(List<ApiKeyExtractor> extractors) {
        this.extractors.addAll(extractors);
    }

    @SuppressWarnings("unused")
    public List<ApiKeyExtractor> getExtractors() {
        return extractors;
    }
}
