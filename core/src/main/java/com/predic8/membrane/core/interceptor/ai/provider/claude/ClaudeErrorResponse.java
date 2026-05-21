package com.predic8.membrane.core.interceptor.ai.provider.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaudeErrorResponse {

    private static final ObjectMapper om = new ObjectMapper();

    private String type = "error";
    private ClaudeError error;
    private String request_id;

    public static ClaudeErrorResponse builder() {
        return new ClaudeErrorResponse();
    }

    public String getType() {
        return type;
    }

    public ClaudeErrorResponse type(String type) {
        this.type = type;
        return this;
    }

    public ClaudeError getError() {
        return error;
    }

    public ClaudeErrorResponse error(ClaudeError error) {
        this.error = error;
        return this;
    }

    public String getRequest_id() {
        return request_id;
    }

    public ClaudeErrorResponse requestId(String requestId) {
        this.request_id = requestId;
        return this;
    }

    public String toJson() {
        try {
            return om.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ClaudeErrorResponse", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClaudeError {

        private String type;
        private String message;

        public static ClaudeError builder() {
            return new ClaudeError();
        }

        public String getType() {
            return type;
        }

        public ClaudeError type(String type) {
            this.type = type;
            return this;
        }

        public String getMessage() {
            return message;
        }

        public ClaudeError message(String message) {
            this.message = message;
            return this;
        }

        @Override
        public String toString() {
            return "ClaudeError{" +
                    "type='" + type + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ClaudeErrorResponse{" +
                "type='" + type + '\'' +
                ", error=" + error +
                ", request_id='" + request_id + '\'' +
                '}';
    }
}