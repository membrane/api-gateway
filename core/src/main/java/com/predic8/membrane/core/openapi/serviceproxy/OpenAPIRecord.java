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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.util.*;
import io.swagger.v3.oas.models.*;

import java.net.*;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;

public class OpenAPIRecord {

    /**
     * Specification parsed with the OpenAPI parser
     */
    OpenAPI api;

    /**
     * Specification parsed with JSON/YAML parser
     */
    JsonNode node;
    OpenAPISpec spec;

    /**
     * Version of the OpenAPI standard e.g. 2.0, 3.0.1
     */
    String version;

    /**
     * Used for tests
     */
    public OpenAPIRecord() {}

    public OpenAPIRecord(OpenAPI api, JsonNode node, OpenAPISpec spec) {
        this.api = api;
        this.node = node;
        this.spec = spec;

        // If used without a node. Version is read from JSON cause JSON
        // supports any version number like 3.1.0 or 3.0.9. For
        // getSpecVerison() there are just a number of enum constants.
        if (node != null) {
            this.version = getOpenAPIVersion(node);
        } else {
            this.version = api.getSpecVersion().name();
        }


    }

    public boolean isVersion2() {
        return version.startsWith("2");
    }

    public boolean isVersion3() {
        return version.startsWith("3");
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
