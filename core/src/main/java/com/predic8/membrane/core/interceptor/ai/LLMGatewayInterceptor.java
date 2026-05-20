package com.predic8.membrane.core.interceptor.ai;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.ai.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.ai.provider.LLMProvider;
import com.predic8.membrane.core.interceptor.ai.provider.LLMRequest;
import com.predic8.membrane.core.interceptor.ai.store.AiApiStore;
import com.predic8.membrane.core.interceptor.ai.store.AiApiUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.json.JsonUtil.setJsonBody;

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
    private int maxOutputTokens;
    private int maxInputTokens;
    private List<String> models;

    private AiApiStore store;

    @Override
    public void init() {
        errorCreator = provider.getErrorCreator();
        if (store != null)
            store.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {

        LLMRequest aiReq;
        try {
            aiReq = provider.getLLMRequest(exc);
        } catch (Exception e) {
            user(router.getConfiguration().isProduction(), "AI Gateway")
                    .title("Invalid request")
                    .detail("Error parsing request: " + e.getMessage())
                    .buildAndSetResponse(exc);
            return RETURN;
        }

        if (!exc.getRequest().isPOSTRequest()) {
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
        }

        long inputTokens = 0;
        if (exc.getRequest().isPOSTRequest()) {
            inputTokens = aiReq.estimateInputTokens();
            log.debug("Estimated input tokens: {}", inputTokens);
            if (store != null) {
                var remaining = store.checkLimit(user, inputTokens, maxOutputTokens);
                log.debug("User {} has {} remaining tokens left", user, remaining);
                if (remaining <= 0) {
                    log.info("Token limit exceeded. Remaining: {} input: {} maxOutput: {}",remaining, inputTokens, maxOutputTokens);
                    exc.setResponse(errorCreator.tokenLimitExceeded(inputTokens+maxOutputTokens, remaining, store.getRemainingResetTime()));
                    return RETURN;
                }
            }
        }
        exc.setProperty(MEMBRANE_AI_USER, user);


        // If APIKey is specified, use that for the LLM. Overwrites keys from the client
        if (apiKey != null) {
            aiReq.setApiKey(apiKey);
        }

        log.debug("Requested model: {}", aiReq.getModel());

        var requestedMaxOutputTokens = aiReq.getRequestedMaxOutputTokens();

        if (maxOutputTokens != 0 && (requestedMaxOutputTokens == -1 || requestedMaxOutputTokens > maxOutputTokens)) {
            log.info("Requested max. output tokens {} exceed the limit. Setting limit to {}.",requestedMaxOutputTokens, maxOutputTokens);
            aiReq.setMaxOutputTokens(maxOutputTokens);
        }

        if (maxInputTokens != 0) {
            if (inputTokens > maxInputTokens) {
                log.info("Input tokens {} exceed the limit of {}.",inputTokens, maxInputTokens);
                exc.setResponse(errorCreator.contextLengthExceeded(maxInputTokens, inputTokens));
                return RETURN;
            }
        }

        if (models != null) {
            var model = aiReq.getModel();
            if (!models.contains(model)) {
                exc.setResponse(errorCreator.modelNotAllowed(model, models));
                return RETURN;
            }
        }

        log.debug("Agent provides the tools: {}", aiReq.getTools());

        setJsonBody(exc.getRequest(), aiReq.getJson());
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {

        provider.getLLMResponse(exc, res -> {
            var user = exc.getProperty(MEMBRANE_AI_USER, AiApiUser.class);
            if (log.isInfoEnabled() && user != null) {
                log.debug("Token usage of user {}: {}", user, res.getUsage());
            } else {
                log.info("Token usage: {}", res.getUsage());
            }
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
     * @param apiKey
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
    @MCChildElement(allowForeign = true, order = 10)
    public void setAiStore(AiApiStore store) {
        this.store = store;
    }

    @Override
    public String getDisplayName() {
        return "LLM Gateway";
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * @param maxOutputTokens
     * @description Maximum number of tokens the LLM should use to generate a response. This is just a hint that the gateway
     * sends to the LLM provider. The provider may use a different limit.
     * @default 0 (unlimited)
     */
    @MCAttribute
    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    /**
     * @param maxInputTokens
     * @description Restricts token usage for the input. The size of the input is estimated by gateway based on the request size.
     * Actual token usage may be deviate from this value.
     */
    @MCAttribute
    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    public List<String> getModels() {
        return models;
    }

    /**
     * @desciption Restricts the models that can be used by the gateway.
     * @param models
     * @default null (no restriction)
     */
    @MCAttribute
    public void setModels(List<String> models) {
        this.models = models;
    }

    public LLMProvider getProvider() {
        return provider;
    }

    /**
     * @param provider The LLM provider to use.
     * @description The LLM provider to use. Currently, OpenAI, Anthropic and Gemini are supported.
     * The provider determines the API used to talk to the LLM. The provider can be different as long as the API is supported.
     */
    @MCChildElement(allowForeign = true)
    public void setProvider(LLMProvider provider) {
        this.provider = provider;
    }
}
