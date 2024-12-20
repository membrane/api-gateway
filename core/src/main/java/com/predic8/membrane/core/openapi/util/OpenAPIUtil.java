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

package com.predic8.membrane.core.openapi.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.X_MEMBRANE_ID;
import static com.predic8.membrane.core.openapi.util.Utils.normalizeForId;

public class OpenAPIUtil {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIUtil.class.getName());

    private static final Pattern hostPortPattern = Pattern.compile("//(.*):(.*)/");

    public static String getIdFromAPI(OpenAPI api) {
        if (api.getInfo().getExtensions() != null) {
            String id = (String) api.getInfo().getExtensions().get(X_MEMBRANE_ID);
            if (id != null)
                return normalizeForId(id + getVersionSuffix(api));
        }
        return normalizeForId(api.getInfo().getTitle() + "-v" + api.getInfo().getVersion());
    }

    private static String getVersionSuffix(OpenAPI api) {
        return "-v" + api.getInfo().getVersion();
    }


    public static boolean isOpenAPI3(JsonNode node) {
        return node.get("openapi") != null && node.get("openapi").asText().startsWith("3");
    }

    public static boolean isSwagger2(JsonNode node) {
        return node.get("swagger") != null && node.get("swagger").asText().startsWith("2");
    }
}
