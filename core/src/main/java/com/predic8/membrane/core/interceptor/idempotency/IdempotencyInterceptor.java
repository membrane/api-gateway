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
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractLanguageInterceptor;
import com.predic8.membrane.core.lang.ExchangeExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;


/**
 * @description <p>Prevents duplicate request processing based on a dynamic idempotency key.</p>
 *
 * <p>This interceptor evaluates an expression (e.g., from headers or body) to extract an idempotency key.
 * If the key has already been processed, it aborts the request with a 409 response.</p>
 *
 * <p>Useful for handling retries from clients to avoid duplicate side effects like double payment submissions.</p>
 *
 * <p>A key counts as processed as soon as its request arrives, also if processing it fails later, e.g. because the
 * backend is not reachable. Further requests with that key are rejected until the key expires. To try again, the
 * client has to use a new key.</p>
 *
 * <p>The interceptor sees each request only once and cannot prevent Membrane's HTTP client from sending it again:
 * a request with an idempotent method like GET, PUT or DELETE may be repeated after a read timeout, a reset
 * connection or, if failover on 5xx is configured, a server error. If the backend does not handle these methods
 * idempotently, disable retries of the HTTP client. POST and PATCH are only repeated when the connection could not
 * be established, which is safe.</p>
 *
 * <p>Retries are disabled in the global HTTP client configuration, which applies to all APIs:</p>
 * <pre><code>
 * configuration:
 *   httpClientConfig:
 *     retries:
 *       retries: 0
 * </code></pre>
 * @topic 3. Security and Validation
 */
@MCElement(name = "idempotency")
public class IdempotencyInterceptor extends AbstractLanguageInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);

    private String key;
    private ExchangeExpression exchangeExpression;
    private int expiration = 3600;
    private Cache<String, Boolean> processedKeys;

    @Override
    public void init() {
        super.init();

        exchangeExpression = expression(this, language, key);
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

        // Check and mark in one atomic step, otherwise concurrent requests with the same key could all pass
        if (processedKeys.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            return handleDuplicateKey(exc, key);
        }

        return CONTINUE;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key;
    }

    private Outcome handleDuplicateKey(Exchange exc, String key) {
        log.info("Idempotency key {} has already been processed.", key);
        user(router.getConfiguration().isProduction(), "idempotency")
                .status(409)
                .detail("Idempotency key has already been processed.", true)
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
