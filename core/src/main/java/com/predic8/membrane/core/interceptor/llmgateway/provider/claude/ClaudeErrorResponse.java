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

package com.predic8.membrane.core.interceptor.llmgateway.provider.claude;

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