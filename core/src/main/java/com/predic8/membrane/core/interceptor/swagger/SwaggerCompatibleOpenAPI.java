package com.predic8.membrane.core.interceptor.swagger;

import io.swagger.v3.oas.models.OpenAPI;

public class SwaggerCompatibleOpenAPI extends OpenAPI {

    void setHost(String host) {}

    String getHost() {
        return "TODO";
    }

    String getBasePath() {
        return "TODO";
    }
}
