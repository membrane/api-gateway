package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.ai.store.AiApiStore;
import com.predic8.membrane.core.interceptor.ai.store.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.ai.AiUtil.estimateTokens;
import static com.predic8.membrane.core.interceptor.ai.AiUtil.extractBearerToken;
import static com.predic8.membrane.core.interceptor.ai.OpenAiApiUtil.*;

@MCElement(name = "openAI")
public class OpenAIAPIInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OpenAIAPIInterceptor.class);

    public static final String MEMBRANE_AI_USERTOKEN = "membrane.ai.usertoken";
    public static final String MAX_OUTPUT_TOKENS = "max_output_tokens";

    private static final ObjectMapper om = new ObjectMapper();

    private String apiKey;
    private int maxOutputTokens;
    private int maxInputTokens;
    private AiApiStore store;

    @Override
    public void init() {
        if (store != null)
            store.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        var header = exc.getRequest().getHeader();

        if (store != null) {
            var user = store.getUser(extractBearerToken(header));
            log.debug("User: {}", user);
            if (user.isEmpty()) {
                exc.setResponse(authenticationFailed());
                return RETURN;
            }
            var remaining = store.checkLimit(user.get());
            if (remaining <= 0) {
                exc.setResponse(tokenLimitExceeded());
                return RETURN;
            }
            exc.setProperty(MEMBRANE_AI_USERTOKEN, user);
        }

        header.removeFields(AUTHORIZATION);
        header.add(AUTHORIZATION, "Bearer " + apiKey);

        var json = getJson(exc, REQUEST);

        if (maxOutputTokens != 0) {
            json.put(MAX_OUTPUT_TOKENS, maxOutputTokens);
        }

        if (maxInputTokens != 0) {
            var input = json.get("input");
            if (input != null) {
                var estimated = estimateTokens(input.asText());
                if (estimated > maxInputTokens) {
                    exc.setResponse(contextLengthExceeded(maxInputTokens, estimated));
                    return RETURN;
                }
            }
        }
        setJsonResponse(exc, json);
        return CONTINUE;
    }

    private static void setJsonResponse(Exchange exc, ObjectNode json) {
        try {
            exc.getRequest().setBodyContent(om.writeValueAsBytes(json));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static ObjectNode getJson(Exchange exc, Flow flow) {
        try {
            if (om.readTree(exc.getMessage(flow).getBodyAsStreamDecoded()) instanceof ObjectNode on) {
                return on;
            }
            throw new RuntimeException("Expected JSON Object");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        var response = exc.getResponse();
        if (!response.isJSON()) {
            log.debug("Response is not JSON");
            return CONTINUE;
        }

        var json = getJson(exc, RESPONSE);

        // Error from AI API
        if (!json.get("error").isNull()) {
            return CONTINUE;
        }
        store.store(exc.getProperty(MEMBRANE_AI_USERTOKEN, String.class), getUsage(json));
        return CONTINUE;
    }

    private Usage getUsage(ObjectNode json) {
        var usage = json.get("usage");
        if (usage == null) {
            return new Usage(0, 0, 0);
        }
        return new Usage(usage.get("input_tokens").asInt(), usage.get("output_tokens").asInt(), usage.get("total_tokens").asInt());
    }



    public String getApiKey() {
        return apiKey;
    }

    @MCAttribute
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public AiApiStore getAiStore() {
        return null;
    }

    @MCChildElement(allowForeign = true)
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
}
