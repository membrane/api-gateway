package com.predic8.membrane.core.interceptor.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractLLMErrorCreator implements LLMErrorCreator {

    private static final ObjectMapper om = new ObjectMapper();

    public static String createJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return """
                    { "error": "Could not create JSON" }
                    """;
        }
    }

    public String envelope(String message, String type, String param, String code) {
        return createJson(new ErrorEnvelope(new ErrorBody(message,type,param,code)));
    }

    private record ErrorEnvelope(ErrorBody error) {
    }

    private record ErrorBody(
            String message,
            String type,
            String param,
            String code
    ) {
    }
}
