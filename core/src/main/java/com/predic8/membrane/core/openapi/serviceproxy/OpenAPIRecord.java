package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.*;
import io.swagger.v3.oas.models.*;

public class OpenAPIRecord {

    OpenAPI api;
    JsonNode node;

    public OpenAPIRecord(OpenAPI api, JsonNode node) {
        this.api = api;
        this.node = node;
    }
}
