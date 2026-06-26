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

package com.predic8.membrane.core.interceptor.llmgateway;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMProvider;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMRequest;
import com.predic8.membrane.core.interceptor.llmgateway.store.AiApiStore;
import com.predic8.membrane.core.interceptor.llmgateway.store.AiApiUser;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.json.JsonUtil.setJsonBody;

/**
 * @description Gateway in front of an LLM provider's chat API (OpenAI, Anthropic/Claude, or Google Gemini). It can
 * share a single provider API key among many clients, authenticate clients and enforce per-user token limits through a
 * store, apply usage policies, and inject a system prompt; token usage is recorded when the response returns. Place it
 * in the flow of an api whose target is the provider's API. Without a store the gateway is stateless and just forwards
 * requests with the configured key and policies. See the tutorials under tutorials/ai/llm-gateway.
 * <pre>
 * llmGateway:
 *   [ apiKey: &lt;provider-key&gt; ]
 *   claude | openai | google              # the provider (required)
 *   [ policies: ... ]                     # token limits, model rules
 *   [ systemPrompt: ... ]
 *   [ simpleStore | jdbcAiApiUsageStore ] # enables per-user auth and limits
 * </pre>
 * @topic 10. AI
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - llmGateway:
 *         claude: {}
 *         policies:
 *           maxInputTokens: 100
 *           maxOutputTokens: 200
 *   target:
 *     url: https://api.anthropic.com
 * </code></pre>
 */
@MCElement(name = "llmGateway")
public class LLMGatewayInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LLMGatewayInterceptor.class);

    public static final String MEMBRANE_AI_USER = "membrane.ai.user";

    private LLMProvider provider;
    private LLMErrorCreator errorCreator;

    private String apiKey;

    private Policies policies = new NullPolicies();

    private SystemPrompt systemPrompt;

    private AiApiStore store;

    @Override
    public void init() {
        super.init();
        errorCreator = provider.getErrorCreator();
        policies.init(errorCreator);
        if (store != null)
            store.init(router);

        // Check if the replacement markers are still there
        if (apiKey != null && apiKey.contains("<<") && apiKey.contains(">>")) {
            throw new ConfigurationException("The configuration contains the replacement marker %s. Substitute it with the API key of the model.".formatted(apiKey));
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {

        LLMRequest aiReq;
        try {
            aiReq = provider.getLLMRequest(exc);
        } catch (Exception e) {
            exc.setResponse(errorCreator.invalidRequestError("Error parsing request: " + e.getMessage()));
            return RETURN;
        }

        if (!exc.getRequest().isPOSTRequest()) {
            if (apiKey != null)
                aiReq.setApiKey(apiKey);
            return CONTINUE;
        }

        AiApiUser user = null;
        if (store != null) {
            var opt = store.getUser(aiReq.getApiKey());
            if (opt.isEmpty()) {
                exc.setResponse(errorCreator.authenticationFailed());
                return RETURN;
            }
            user = opt.get();
            log.debug("User: {}", user);
            exc.setProperty(MEMBRANE_AI_USER, user);
        }

        long inputTokens = aiReq.estimateInputTokens();
        log.debug("Estimated input tokens: {}", inputTokens);

        // Check store limits
        if (store != null) {
            var effectiveMaxTokens = computeEffectiveMaxOutputTokens(aiReq.getRequestedMaxOutputTokens(), policies.getMaxOutputTokens());
            var remaining = store.checkLimit(user, inputTokens, effectiveMaxTokens);
            log.debug("User {} has {} remaining tokens left", user, remaining);
            if (remaining <= 0) {
                log.info("Token limit exceeded. Remaining: {} input: {} maxOutput: {}", remaining, inputTokens, effectiveMaxTokens);
                exc.setResponse(errorCreator.tokenLimitExceeded(inputTokens + effectiveMaxTokens, remaining, store.getRemainingResetTime()));
                return RETURN;
            }
        }

        // If APIKey is specified, use that for the LLM. Overwrites keys from the client
        if (apiKey != null) {
            aiReq.setApiKey(apiKey);
        }

        log.debug("Requested model: {}", aiReq.getModel());

        var outcome = policies.handleRequest(aiReq,exc);
        if (outcome != CONTINUE) {
            return outcome;
        }

        if (systemPrompt != null) {
            outcome = systemPrompt.handleRequest(aiReq,exc);
            if (outcome != CONTINUE) {
                return outcome;
            }
        }

        setJsonBody(exc.getRequest(), aiReq.getJson());
        return CONTINUE;
    }

    long computeEffectiveMaxOutputTokens(long requestedMaxOutputTokens, long maxOutputTokens) {
        if (requestedMaxOutputTokens <= 0)
            return maxOutputTokens;
        return Math.min(requestedMaxOutputTokens, maxOutputTokens);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        provider.getLLMResponse(exc, res -> {
            var user = exc.getProperty(MEMBRANE_AI_USER, AiApiUser.class);
            log.debug("Token usage of user {}: {}", user, res.getUsage());
            if (store != null) {
                store.store(user, res.getUsage());
            }
        });

        return CONTINUE;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * @description API key for the LLM provider, used for all upstream calls and overriding any key sent by the client.
     * Use the key issued by OpenAI, Anthropic, or Google.
     * @example sk-...
     */
    @MCAttribute
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public AiApiStore getAiStore() {
        return store;
    }

    /**
     * @description Backing store that makes the gateway stateful: it authenticates clients by their API key, enforces
     * per-user token limits, and records usage. Omit it to run the gateway statelessly.
     */
    @MCChildElement(allowForeign = true, order = 30)
    public void setAiStore(AiApiStore store) {
        this.store = store;
    }

    @Override
    public String getDisplayName() {
        return "LLM Gateway";
    }

    public LLMProvider getProvider() {
        return provider;
    }

    /**
     * @description The LLM provider whose API is exposed, for example <code>claude</code>, <code>openai</code>, or
     * <code>google</code>. Determines the wire format used to talk to the model.
     */
    @MCChildElement(order = 10)
    public void setProvider(LLMProvider provider) {
        this.provider = provider;
    }

    public Policies getPolicies() {
        return policies;
    }

    /**
     * @description Usage policies applied to each request, such as maximum input and output tokens or allowed models.
     */
    @MCChildElement(order = 20)
    public void setPolicies(Policies policies) {
        this.policies = policies;
    }

    public SystemPrompt getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * @description System prompt injected into every request before it is forwarded to the provider.
     */
    @MCChildElement
    public void setSystemPrompt(SystemPrompt systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
