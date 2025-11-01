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

import com.google.common.cache.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.lang.*;

import java.util.concurrent.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;

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
public class IdempotencyInterceptor extends AbstractLanguageInterceptor {

    private String key;
    private ExchangeExpression exchangeExpression;
    private int expiration = 3600;
    private Cache<String, Boolean> processedKeys;

    @Override
    public void init() {
        super.init();
        exchangeExpression = ExchangeExpression.newInstance(this, language, key);
        processedKeys = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(expiration, TimeUnit.SECONDS)
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
                .status(409)
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
    public void setExpiration(int expiration) {
        this.expiration = expiration;
    }

    public String getKey() {
        return key;
    }

    public int getExpiration() {
        return expiration;
    }
}
