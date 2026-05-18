package com.predic8.membrane.core.interceptor.ai;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
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
import static com.predic8.membrane.core.interceptor.ai.LLMApiUtil.*;
import static com.predic8.membrane.core.util.json.JsonUtil.setJsonBody;

@MCElement(name = "aiGateway")
public class LLMGatewayInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LLMGatewayInterceptor.class);

    public static final String MEMBRANE_AI_USER = "membrane.ai.user";

    private LLMProvider provider;

    private String apiKey;
    private int maxOutputTokens;
    private int maxInputTokens;
    private List<String> models;

    private AiApiStore store;

    @Override
    public void init() {
        if (store != null)
            store.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {

        LLMRequest aiReq;
        try {
            aiReq = provider.getLLMRequest(exc);
        } catch (Exception e) {
            user(router.getConfiguration().isProduction(),"AI Gateway")
                    .title("Invalid request")
                    .detail("Error parsing request: " + e.getMessage())
                    .buildAndSetResponse(exc);
            return RETURN;
        }

        if (!exc.getRequest().isPOSTRequest()) {
            aiReq.setApiKey(apiKey);
            return CONTINUE;
        }

        long inputTokens = 0;

        if (store != null) {
            var opt = store.getUser(aiReq.getApiKey());
            if (opt.isEmpty()) {
                exc.setResponse(authenticationFailed());
                return RETURN;
            }
            var user = opt.get();
            log.debug("User: {}", user);
            if (exc.getRequest().isPOSTRequest()) {
                inputTokens = aiReq.estimateInputTokens();
                var remaining = store.checkLimit(user, inputTokens, maxOutputTokens);
                if (remaining <= 0) {
                    exc.setResponse(tokenLimitExceeded());
                    return RETURN;
                }
            }
            exc.setProperty(MEMBRANE_AI_USER, user);
        }

        // TODO if no apiKey in config => use key from client
        aiReq.setApiKey(apiKey);

        if (maxOutputTokens != 0) {
            aiReq.setMaxOutputTokens(maxOutputTokens);
        }

        if (maxInputTokens != 0) {
            if (inputTokens > maxInputTokens) {
                exc.setResponse(contextLengthExceeded(maxInputTokens, inputTokens));
                return RETURN;
            }
        }

        if (models != null) {
            var model = aiReq.getModel();
            if (!models.contains(model)) {
                exc.setResponse(modelNotAllowed(model, models));
                return RETURN;
            }
        }

        log.debug("Tools: {}", aiReq.getTools());

        setJsonBody(exc.getRequest(), aiReq.getJson());
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {

        var aiRes = provider.getLLMResponse(exc, res -> {
            if (store != null) {
                store.store(exc.getProperty(MEMBRANE_AI_USER, AiApiUser.class), res.getUsage());
            }
        });

        return CONTINUE;
    }

    public String getApiKey() {
        return apiKey;
    }

    @MCAttribute
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public AiApiStore getAiStore() {
        return store;
    }

    @MCChildElement(allowForeign = true, order = 10)
    public void setAiStore(AiApiStore store) {
        this.store = store;
    }

    @Override
    public String getDisplayName() {
        return "OpenAI API";
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    @MCAttribute
    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    @MCAttribute
    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    public List<String> getModels() {
        return models;
    }

    @MCAttribute
    public void setModels(List<String> models) {
        this.models = models;
    }

    public LLMProvider getProvider() {
        return provider;
    }

    @MCChildElement(allowForeign = true)
    public void setProvider(LLMProvider provider) {
        this.provider = provider;
    }
}
