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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.apikey.extractors.*;
import com.predic8.membrane.core.interceptor.apikey.stores.*;
import com.predic8.membrane.core.security.*;
import org.slf4j.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.stream.Stream.*;

/**
 * @description Validates API keys extracted from incoming requests and looks up permissions (scopes) via configured key stores.
 * Extractors can read the keys from HTTP headers, query parameters and may other message part. When validation succeeds, the interceptor adds an
 * {@code ApiKeySecurityScheme} with the resolved scopes to the {@code Exchange}. Scopes can be checked in later plugins
 * using the SpEL function {@code hasScope("...")}.
 * <p>
 * Typical configuration:
 * </p>
 * <pre>
 * &lt;api&gt;
 *   &lt;apiKey required="true"&gt;
 *     &lt;!-- one or more key stores --&gt;
 *     &lt;.../ &gt;
 *
 *     &lt;!-- optional: customize extraction (header/query) --&gt;
 *     &lt;headerExtractor name="X-Api-Key"/&gt;
 *   &lt;/apiKey&gt;
 * &lt;/api&gt;
 * </pre>
 * <p>
 * On missing or invalid keys, a Problem Details response is generated (401 for missing, 403 for invalid) unless
 * {@code required="false"} is set.
 * </p>
 * @topic 3. Security and Validation
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
                .collect(Collectors.joining());
    }

    @Override
    public void init() {
        super.init();
        stores.addAll(router.getBeanFactory().getBeansOfType(ApiKeyStore.class).values());
        stores.forEach(s -> s.init(router));
        extractors.forEach(e -> e.init(router));
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        var key = getKey(exc);
        if (required && key.isEmpty()) {
            log.warn("Tried access apiKey protected resource without key. Uri: {}", exc.getRequestURI());
            security(false, getDisplayName())
                    .title(TITLE_4XX)
                    .statusCode(401)
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
                log.warn("The provided API {} key is invalid.", key.get());
                security(false, getDisplayName())
                        .title(TITLE_4XX)
                        .statusCode(403)
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
     * @description Controls whether API key validation is enforced. If set to {@code false}, the interceptor still extracts
     * keys and loads scopes so they remain available for downstream checks (e.g., via {@code hasScope("...")}), but requests
     * without a valid key are allowed to pass.
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
     * @description Defines the API key stores used to resolve and authorize keys. Provide one or more child elements that
     * implement a store (e.g., file-based, in-memory. jdbc or mongodb). Scopes from multiple stores are combined.
     * <p>
     * Example:
     * </p>
     * <pre>
     * &lt;apiKey&gt;
     *   &lt;!-- store elements; order does not matter --&gt;
     *   &lt;yourFileStore src="classpath:keys.txt"/&gt;
     *   &lt;yourXmlStore  ref="sharedKeysBean"/&gt;
     * &lt;/apiKey&gt;
     * </pre>
     */
    @MCChildElement(allowForeign = true)
    public void setStores(List<ApiKeyStore> stores) {
        this.stores.addAll(stores);
    }

    public List<ApiKeyStore> getStores() {
        return stores;
    }

    /**
     * @description Configures how and where API keys are extracted from requests (e.g., HTTP header or URL query parameter).
     * Provide one or more extractor elements. If omitted, a header extractor using {@code X-Api-Key} is used.
     * @default &lt;headerExtractor /&gt; (header name {@code X-Api-Key})
     * <p>
     * Examples:
     * </p>
     * <pre>
     * &lt;apiKey&gt;
     *   &lt;!-- header: X-Api-Key (default) --&gt;
     *   &lt;headerExtractor /&gt;
     *
     *   &lt;!-- custom header --&gt;
     *   &lt;headerExtractor name="Authorization" prefix="Api-Key "/&gt;
     *
     *   &lt;!-- query parameter --&gt;
     *   &lt;queryParamExtractor name="api_key"/&gt;
     * &lt;/apiKey&gt;
     * </pre>
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
