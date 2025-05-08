/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.idempotency;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;

import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;

/**
 * @description <p>Prevents duplicate request processing based on a dynamic idempotency key.</p>
 *
 * <p>This interceptor evaluates an expression (e.g., from headers or body) to extract an idempotency key.
 * If the key has already been processed, it aborts the request with a 409 response.</p>
 *
 * <p>Useful for handling retries from clients to avoid duplicate side effects like double payment submissions.</p>
 * @topic 3. Security and Validation
 */
@MCElement(name = "idempotency")
public class IdempotencyInterceptor extends AbstractInterceptor {

    private String key;
    private ExchangeExpression exchangeExpression;
    private Language language = SPEL;
    private int expirationSeconds = 3600;
    private Cache<String, Boolean> processedKeys;

    @Override
    public void init() {
        super.init();
        exchangeExpression = ExchangeExpression.newInstance(router, language, key);
        processedKeys = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(expirationSeconds, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        String key = normalizeKey(exchangeExpression.evaluate(exc, REQUEST, String.class));
        if (key.isEmpty()) {
            return CONTINUE;
        }

        if (processedKeys.getIfPresent(key) != null) {
            return handleDuplicateKey(exc, key);
        }
        processedKeys.put(key, Boolean.TRUE);

        return CONTINUE;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key;
    }

    private Outcome handleDuplicateKey(Exchange exc, String key) {
        user(false, "idempotency")
                .statusCode(409)
                .detail("key %s has already been processed".formatted(key))
                .buildAndSetResponse(exc);
        return ABORT;
    }

    @Override
    public String getDisplayName() {
        return "Idempotency";
    }

    /**
     * @description Expression used to extract the idempotency key from the exchange.
     * Can be an XPath, JSONPath, header, or other supported syntax depending on the language.
     * @example $.id
     */
    @MCAttribute
    @Required
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * @description Language used to interpret the expression (e.g., spel, jsonpath, xpath).
     * Determines how the key string will be evaluated.
     * @default SpEL
     * @example jsonpath
     */
    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    /**
     * @description Time in seconds after which idempotency keys automatically expire.
     * Useful to avoid memory leaks in long-running systems.
     * Common values:
     * <ul>
     * <li>300 seconds = 5 minutes</li>
     * <li>86400 seconds = 1 day</li>
     * <li>604800 seconds = 1 week</li>
     * <li>2592000 seconds = 1 month (30 days)</li>
     * </ul>
     * @default 3600
     */
    @MCAttribute
    public void setExpirationSeconds(int expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }

    public String getKey() {
        return key;
    }

    public Language getLanguage() {
        return language;
    }

    public int getExpirationSeconds() {
        return expirationSeconds;
    }
}
