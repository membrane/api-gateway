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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.apikey.extractors.*;
import com.predic8.membrane.core.interceptor.apikey.stores.*;
import com.predic8.membrane.core.security.*;
import org.slf4j.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.stream.Stream.*;

/**
 * @description Secures APIs by validating keys stored in either files or proxies.xml. Keys can be received from clients via HTTP headers or URL query parameters. Additional permission checks are possible through scope validation - scopes are loaded into an Exchange property and can be checked using the "hasScope()" SpEL function.
 * @topic 6. Security
 */
@MCElement(name = "apiKey")
public class ApiKeysInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeysInterceptor.class.getName());

    public static final String SCOPES = "membrane-scopes";
    public static final String TYPE_4XX = "authorization-denied";
    public static final String TITLE_4XX = "Access Denied";
    private final List<ApiKeyStore> stores = new ArrayList<>();
    private final List<ApiKeyExtractor> extractors = new ArrayList<>();
    private boolean required = true;

    public ApiKeysInterceptor() {
        name = "api key";
    }

    @Override
    public String getShortDescription() {
        return required ? "Secures access with api keys and RBAC with scopes. "
               : "Warning: Required is set to <code>false</code>, scopes will be extracted but any api key, even missing ones, will be accepted.";
    }

    @Override
    public String getLongDescription() {
        return getShortDescription() + "<br/>" +  extractors.stream()
                .map(extractor -> extractor.getDescription() + "<br/>")
                .collect(Collectors.joining());
    }

    @Override
    public void init() {
        super.init();
        stores.addAll(router.getBeanFactory().getBeansOfType(ApiKeyStore.class).values());
        stores.forEach(apiKeyStore -> apiKeyStore.init(router));
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        var key = getKey(exc);
        if (required && key.isEmpty()) {
            log.warn("Tried access apiKey protected resource without key. Uri: {}",exc.getRequestURI());
            exc.setResponse(ProblemDetails.security(false)
                            .statusCode(401)
                            .addSubType(TYPE_4XX)
                            .title(TITLE_4XX)
                    .detail("Tried to access API key protected resource without key.")
                    .build());
            return RETURN;
        }

        if (key.isPresent()) {
            try {
                var k = key.get();
                new ApiKeySecurityScheme(k.location(), k.name()).scopes(getScopes(k.key())).add(exc);
            } catch (UnauthorizedApiKeyException e) {
                if (!required) {return CONTINUE;}
                log.warn("The provided API {} key is invalid.",key.get());
                exc.setResponse(ProblemDetails.security(false)
                                .statusCode(403)
                                .addSubType(TYPE_4XX)
                                .title(TITLE_4XX)
                                .component(getDisplayName())
                                .detail("The provided API key is invalid.")
                                .build());
                return RETURN;
            }
        }
        return CONTINUE;
    }

    public Set<String> getScopes(String key) throws UnauthorizedApiKeyException {
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

        return new HashSet<>(combinedScopes);
    }

    public Optional<LocationNameValue> getKey(Exchange exc) {
        return extractors.stream()
                         .flatMap(ext -> ofNullable(
                                 ext.extract(exc).orElse(null)
                         ))
                         .findFirst();
    }

    /**
     * @description Controls whether API key validation is enforced or optional. Optional will still load scopes and make them available for checking through SpEL function "hasScope()".
     * @default true
     * @example false
     */
    @SuppressWarnings("SameParameterValue")
    @MCAttribute
    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }

    /**
     * @description API key stores to validate keys against
     */
    @MCChildElement(allowForeign = true)
    public void setStores(List<ApiKeyStore> stores) {
        this.stores.addAll(stores);
    }

    public List<ApiKeyStore> getStores() {
        return stores;
    }

    /**
     * @description Extractors that define where and how to extract API keys from requests
     * @default <headerExtractor /> (Using default header "X-Api-Key")
     */
    @MCChildElement(allowForeign = true, order = 1)
    public void setExtractors(List<ApiKeyExtractor> extractors) {
        this.extractors.addAll(extractors);
    }

    @SuppressWarnings("unused")
    public List<ApiKeyExtractor> getExtractors() {
        return extractors;
    }
}
