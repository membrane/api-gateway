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
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyHeaderExtractor;
import com.predic8.membrane.core.interceptor.apikey.extractors.LocationNameValue;
import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import com.predic8.membrane.core.security.ApiKeySecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.ofNullable;

/**
 * @description Validates an API key extracted from each request and resolves its scopes from the
 * configured stores. On success it adds an <code>ApiKeySecurityScheme</code> carrying the scopes to
 * the <code>Exchange</code>, which later plugins test with <code>hasScope("...")</code>. A missing
 * key returns <code>401</code> and an invalid key <code>403</code> as Problem Details, unless
 * <code>required</code> is <code>false</code>, in which case requests pass and scopes are attached
 * only when a valid key is present.
 * <pre>
 * apiKey:
 *   [ required: true | false ]    # default: true
 *   extractors:                   # 0..*, default: header X-Api-Key
 *     - header: &lt;name&gt; | query: &lt;name&gt;
 *     ...
 *   stores:                       # 1..*
 *     - ...
 * </pre>
 * @topic 3. Security and Validation
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - apiKey:
 *         required: true
 *         extractors:
 *           - header: X-Api-Key
 * </code></pre>
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
        return getShortDescription() + "<br/>" + extractors.stream()
                .map(extractor -> extractor.getDescription() + "<br/>")
                .collect(joining());
    }

    @Override
    public void init() {
        super.init();

        // Todo: Move logic into the registry
        // The beanFactory is only there when the Membrane configuration was read from XML
        if (router.getBeanFactory() != null) {
            stores.addAll(router.getBeanFactory().getBeansOfType(ApiKeyStore.class).values());
        }
        // For YAML configuration
        if (router.getRegistry() != null) {
            this.stores.addAll(router.getRegistry().getBeans(ApiKeyStore.class));
        }

        stores.forEach(s -> s.init(router));

        // Add the default extractor if none is configured
        if (extractors.isEmpty()) {
            extractors.add(new ApiKeyHeaderExtractor());
        }

        extractors.forEach(e -> e.init(router));
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        var key = getKey(exc);
        if (required && key.isEmpty()) {
            log.warn("Tried access apiKey protected resource without key. Uri: {}", exc.getOriginalRelativeURI());
            security(false, getDisplayName())
                    .title(TITLE_4XX)
                    .status(401)
                    .addSubType(TYPE_4XX)
                    .detail("Tried to access API key protected resource without key.")
                    .buildAndSetResponse(exc);
            return RETURN;
        }

        if (key.isPresent()) {
            try {
                var k = key.get();
                new ApiKeySecurityScheme(k.location(), k.name()).scopes(getScopes(k.key())).add(exc);
            } catch (UnauthorizedApiKeyException e) {
                if (!required) {
                    return CONTINUE;
                }
                log.warn("API key is invalid.");
                security(false, getDisplayName())
                        .title(TITLE_4XX)
                        .status(403)
                        .addSubType(TYPE_4XX)
                        .detail("The provided API key is invalid.")
                        .buildAndSetResponse(exc);
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
            } catch (Exception ignored) {
            }
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
     * @description Whether a valid key is required. When <code>false</code>, keys are still extracted
     * and scopes attached, but requests without a valid key pass through.
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
     * @description Key stores that resolve a key to its scopes and authorize it. Scopes from all
     * configured stores are merged. A key unknown to every store is rejected as invalid.
     */
    @MCChildElement(allowForeign = true)
    public void setStores(List<ApiKeyStore> stores) {
        this.stores.addAll(stores);
    }

    public List<ApiKeyStore> getStores() {
        return stores;
    }

    /**
     * @description Where keys are read from. The first extractor that finds a key wins. If omitted,
     * a single <code>header</code> extractor reading <code>X-Api-Key</code> is used.
     * @default header X-Api-Key
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
