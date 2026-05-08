package com.predic8.membrane.core.interceptor.ai;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.ai.provider.AiProvider;
import com.predic8.membrane.core.interceptor.ai.store.AiApiStore;
import com.predic8.membrane.core.interceptor.ai.store.AiApiUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.ai.OpenAiApiUtil.*;
import static com.predic8.membrane.core.util.json.JsonUtil.setJsonBody;

@MCElement(name = "aiGateway")
public class OpenAIAPIInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OpenAIAPIInterceptor.class);

    public static final String MEMBRANE_AI_USER = "membrane.ai.user";

    private AiProvider provider;

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

        var aiReq = provider.getAiApiRequest(exc);

        if (store != null) {
            var opt = store.getUser(aiReq.getApiKey());
            if (opt.isEmpty()) {
                exc.setResponse(authenticationFailed());
                return RETURN;
            }
            var user = opt.get();
            log.debug("User: {}", user);
            var remaining = store.checkLimit(user);
            if (remaining <= 0) {
                exc.setResponse(tokenLimitExceeded());
                return RETURN;
            }
            exc.setProperty(MEMBRANE_AI_USER, user);
        }

        aiReq.setApiKey(apiKey);

        if (maxOutputTokens != 0) {
            aiReq.setMaxOutputTokens(maxOutputTokens);
        }

        if (maxInputTokens != 0) {
            var estimated = aiReq.estimateInputTokens();
            if (estimated > maxInputTokens) {
                exc.setResponse(contextLengthExceeded(maxInputTokens, estimated));
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

        setJsonBody(exc.getRequest(), aiReq.getJson());
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {

        var aiRes = provider.getAiApiResponse(exc);

        if (aiRes.isError())
            return CONTINUE; // pass error from AI API to client

        if (store != null) {
            store.store(exc.getProperty(MEMBRANE_AI_USER, AiApiUser.class), aiRes.getUsage());
        }
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

    public AiProvider getProvider() {
        return provider;
    }

    @MCChildElement(allowForeign = true)
    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }
}
