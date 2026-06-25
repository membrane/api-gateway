/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.openapi.util.OpenAPIUtil;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URIFactory;
import io.swagger.v3.oas.models.OpenAPI;

import java.io.IOException;
import java.net.URISyntaxException;

public class OpenAPIRecord {

    /**
     * Specification parsed with the OpenAPI parser
     */
    OpenAPI api;

    /**
     * Specification parsed with JSON/YAML parser
     */
    JsonNode node;

    /**
     * Config element e.g. from the proxies.xml
     */
    OpenAPISpec spec;

    /**
     * Version of the OpenAPI standard e.g. 3.0.1, 3.1.0
     */
    String version;

    /**
     * Used for tests
     */
    public OpenAPIRecord() {}

    public OpenAPIRecord(OpenAPI api, OpenAPISpec spec) {
        this.api = api;
        this.node = convertToNode(api);
        this.spec = spec;
        this.version = api.getSpecVersion().name();
    }

    /**
     * Creates a record with an explicitly provided document node instead of one serialized from the
     * parsed model. Used for OpenAPI 3.2 documents, where the original node is kept so that
     * publishing stays faithful to constructs the swagger model cannot represent, and the real
     * {@code 3.2.x} version string is reported.
     */
    public OpenAPIRecord(OpenAPI api, JsonNode node, OpenAPISpec spec) {
        this.api = api;
        this.node = node;
        this.spec = spec;
        this.version = api.getOpenapi() != null ? api.getOpenapi() : api.getSpecVersion().name();
    }

    private static JsonNode convertToNode(OpenAPI api) {
        try {
            return OpenAPIUtil.convert2Json(api);
        } catch (IOException e) {
            throw new ConfigurationException("""
                    Cannot convert OpenAPI to JSON.

                    OpenAPI:
                    %s
                    """.formatted(api), e);
        }
    }

    public JsonNode rewriteOpenAPI(Exchange exc, URIFactory uriFactory) throws URISyntaxException {
        return spec.getRewrite().rewrite(this,exc,uriFactory);
    }

    public OpenAPI getApi() {
        return api;
    }

    public OpenAPISpec getSpec() {
        return spec;
    }
}
