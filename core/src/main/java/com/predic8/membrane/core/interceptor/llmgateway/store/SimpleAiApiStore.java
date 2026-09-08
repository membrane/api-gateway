/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.llmgateway.store;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;

/**
 * @description Authenticates clients of the LLM Gateway by their api key and enforces a token limit per user, with the
 * users configured inline and their usage kept in memory. A request whose key is unknown is rejected; a user who has
 * spent the tokens of the current period is rejected until the period resets. Usage counts are lost on restart, so
 * this fits a small, fixed set of clients rather than a large or changing one. See
 * tutorials/ai/llm-gateway/openai/20-Sharing-API-Keys.yaml.
 * <pre>
 * simpleStore:
 *   users:
 *     - name: &lt;name&gt;
 *       apiKey: &lt;key&gt;
 *       [ tokens: &lt;limit&gt; ]
 *     ...
 *   [ limitResetPeriod: &lt;seconds&gt; ]
 *   [ logUsage: true | false ]
 * </pre>
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - llmGateway:
 *         apiKey: sk-...
 *         openai: {}
 *         simpleStore:
 *           users:
 *             - name: alice
 *               apiKey: abc123
 *               tokens: 500
 *             - name: bob
 *               apiKey: qwertz
 *               tokens: 10000
 *           limitResetPeriod: 60
 *   target:
 *     url: https://api.openai.com
 * </code></pre>
 */
@MCElement(name="simpleStore",component = false, id="simple-ai-api-store")
public class SimpleAiApiStore implements AiApiUserStore {

    private static final Logger log = LoggerFactory.getLogger(SimpleAiApiStore.class);

    @GuardedBy("lock")
    private List<AiApiUser> users = Collections.emptyList();

    private boolean logUsage = true;

    private final Object lock = new Object();

    @GuardedBy("lock")
    private Instant nextReset;

    private long limitResetPeriod = 60;

    @Override
    public void init(Router router) {
        synchronized (lock) {
            for (AiApiUser user : users) {
                if (user.getApiKey() == null)
                    throw new ConfigurationException(
                            "The %s of a simpleStore has no apiKey. Every user needs one to authenticate at the gateway."
                                    .formatted(describe(user)));
            }
        }
    }

    private static String describe(AiApiUser user) {
        return user.getName() == null ? "unnamed user" : "user " + user.getName();
    }

    @Override
    public void store(AiApiUser user, Usage usage) {
        if (logUsage)
            log.info("user: {} {}", user.getName(), usage.toString());
        user.addTokensUsedInPeriod(usage);
    }

    @Override
    public Optional<AiApiUser> getUser(String token) {
        if (token == null)
            return Optional.empty();
        synchronized (lock) {
            return users.stream().filter(u -> matches(token, u.getApiKey())).findFirst();
        }
    }

    /**
     * Compares two keys of the same length without returning early on the first differing byte, so
     * the comparison does not leak how much of a guessed key was right. The length itself is not
     * hidden: keys of differing length are rejected immediately.
     */
    private static boolean matches(String token, String apiKey) {
        return MessageDigest.isEqual(token.getBytes(UTF_8), apiKey.getBytes(UTF_8));
    }

    @Override
    public long checkLimit(AiApiUser user, long inputTokens, long outputTokens) {
        if (user == null)
            return 0; // anonymous user gets no tokens

        synchronized (lock) {
            var now = now();
            if (nextReset == null || now.isAfter(nextReset)) {
                nextReset = now.plusSeconds(limitResetPeriod);
                log.info("Resetting AI API token usage limit.");
                users.forEach(AiApiUser::resetTokensUsedInPeriod);
            }
        }

        return user.checkLimit(inputTokens + outputTokens);
    }

    @Override
    public long getRemainingResetTime() {
        synchronized (lock) {
            return nextReset == null ? 0 : (nextReset.toEpochMilli() - now().toEpochMilli()) / 1000;
        }
    }


    /**
     * @description The users the gateway accepts, each with the api key it authenticates by and its token limit. A
     * user without an api key is a configuration error and fails the startup.
     */
    @MCChildElement(allowForeign = true,order = 10)
    public void setUsers(List<AiApiUser> users) {
        synchronized (lock) {
            this.users = users;
        }
    }

    public List<AiApiUser> getUsers() {
        synchronized (lock) {
            return List.copyOf(users);
        }
    }

    public long getLimitResetPeriod() {
        return limitResetPeriod;
    }

    /**
     * @description The period in seconds after which the tokens used by every user are set back to zero and their full
     * limit is available again.
     * @default 60
     * @example 3600
     */
    @MCAttribute
    public void setLimitResetPeriod(long limitResetPeriod) {
        this.limitResetPeriod = limitResetPeriod;
    }

    public boolean isLogUsage() {
        return logUsage;
    }

    /**
     * @description Whether the token usage of every request is written to the log.
     * @default true
     * @example false
     */
    @MCAttribute
    public void setLogUsage(boolean logUsage) {
        this.logUsage = logUsage;
    }
}

