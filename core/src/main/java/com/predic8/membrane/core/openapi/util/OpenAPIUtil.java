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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.transport.http.*;
import io.swagger.v3.oas.models.*;
import org.slf4j.*;

import java.util.regex.*;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.util.Utils.*;

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

    public static String getOpenAPIVersion(JsonNode node) {
        if (isSwagger2(node)) {
            return node.get("swagger").asText();
        } else if (isOpenAPI3(node)) {
            return node.get("openapi").asText();
        }
        log.info("Cannot detect OpenAPI version.");
        return "?";
    }

    public static boolean isOpenAPI3(JsonNode node) {
        return node.get("openapi") != null && node.get("openapi").asText().startsWith("3");
    }

    public static boolean isSwagger2(JsonNode node) {
        return node.get("swagger") != null && node.get("swagger").asText().startsWith("2");
    }

    /**
     * The OpenAPI parser transforms Swagger 2 specs into OpenAPI 3 documents. Swagger has the field host containing
     * only host and port. This field is put into OpenAPI 3 info.server field with the pattern "//HOST:PORT/". This
     * method parses this string and returns a HostColonPort object.
     * @param server String with the pattern //HOST:PORT/
     * @return HostColonPort
     */
    public static HostColonPort parseSwaggersInfoServer(String server) throws Exception {
        Matcher m = hostPortPattern.matcher(server);
        if (m.find()) {
            String host = m.group(1);
            String port = m.group(2);
            return new HostColonPort(false,host,Integer.parseInt(port));
        }
        throw new Exception("Can't parse server string");
    }
}
