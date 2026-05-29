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
import com.predic8.membrane.core.interceptor.llmgateway.provider.ModelInputRequest;
import com.predic8.membrane.core.interceptor.llmgateway.store.AiApiStore;
import com.predic8.membrane.core.interceptor.llmgateway.store.AiApiUser;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

/*
 * @description <p>
 * API Gateway for Large Language Models (LLMs).
 * </p>
 * <b>Features:</b>
 * <ul>
 *   <li>Sharing an API key between multiple users</li>
 *   <li>Enforcing token limits</li>
 *   <li>Logging LLM usage</li>
 * </ul>
 * </p>
 * @topic 10. AI
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

        LLMRequest llmReq;
        try {
            llmReq = provider.getLLMRequest(exc);
        } catch (Exception e) {
            exc.setResponse(errorCreator.invalidRequestError("Error parsing request: " + e.getMessage()));
            return RETURN;
        }

        AiApiUser user = null;
        if (store != null) {
            var opt = store.getUser(llmReq.getApiKey());
            if (opt.isEmpty()) {
                exc.setResponse(errorCreator.authenticationFailed());
                return RETURN;
            }
            user = opt.get();
            log.debug("User: {}", user);
            exc.setProperty(MEMBRANE_AI_USER, user);
        }

        // If APIKey is specified, use that for the LLM. Overwrites keys from the client
        if (apiKey != null) {
            llmReq.setApiKey(apiKey);
        }

        if (!exc.getRequest().isPOSTRequest()) {
            return CONTINUE;
        }

        if (!(llmReq instanceof ModelInputRequest mir)) {
            return CONTINUE;
        }

        var outcome = policies.handleRequest(mir, exc);
        if (outcome != CONTINUE) {
            return outcome;
        }

        if (systemPrompt != null) {
            outcome = systemPrompt.handleRequest(mir, exc);
            if (outcome != CONTINUE) {
                return outcome;
            }
        }

        // Check store limits
        if (checkStoreLimits(exc, mir, user) != CONTINUE) {
            return RETURN;
        }

        exc.getRequest().setBodyContent(mir.getBody().getContent());
        return CONTINUE;
    }

    private Outcome checkStoreLimits(Exchange exc, ModelInputRequest mir, AiApiUser user) {
        long inputTokens = mir.estimateInputTokens();
        log.debug("Estimated input tokens: {}", inputTokens);
        if (store != null) {
            var effectiveMaxTokens = computeEffectiveMaxOutputTokens(mir.getRequestedMaxOutputTokens(), policies.getMaxOutputTokens());
            var remaining = store.checkLimit(user, inputTokens, effectiveMaxTokens);
            log.debug("User {} has {} remaining tokens left", user, remaining);
            if (remaining <= 0) {
                log.info("Token limit exceeded. Remaining: {} input: {} maxOutput: {}", remaining, inputTokens, effectiveMaxTokens);
                exc.setResponse(errorCreator.tokenLimitExceeded(inputTokens + effectiveMaxTokens, remaining, store.getRemainingResetTime()));
                return RETURN;
            }
        }
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
     * @param apiKey LLM provider API key
     * @description API key for the LLM provider. Specify here the API key from OpenAI or Anthropic.
     */
    @MCAttribute
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public AiApiStore getAiStore() {
        return store;
    }

    /**
     * @param store Store for API keys and usage statistics
     * @description The LLM Gateway can operate stateless and statefully. For stateful operation, specify an AiApiStore.
     * A store is needed for user authentication at the gateway.
     * The gateway will use the store to enforce token limits and log usage statistics.
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
     * @param provider The LLM provider to use.
     * @description The LLM provider to use. Currently, OpenAI, Anthropic and Gemini are supported.
     * The provider determines the API used to talk to the LLM. The provider can be different as long as the API is supported.
     */
    @MCChildElement(order = 10)
    public void setProvider(LLMProvider provider) {
        this.provider = provider;
    }

    public Policies getPolicies() {
        return policies;
    }

    /**
     *
     * @param policies Usage policy for the LLM Gateway.
     */
    @MCChildElement(order = 20)
    public void setPolicies(Policies policies) {
        this.policies = policies;
    }

    public SystemPrompt getSystemPrompt() {
        return systemPrompt;
    }

    @MCChildElement
    public void setSystemPrompt(SystemPrompt systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
