/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyStore;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher.PATH;
import static java.lang.String.valueOf;

@MCElement(name = "APIsJSON")
public class ApisJsonInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApisJsonInterceptor.class);

    private static final ObjectMapper om = new ObjectMapper();
    private static final String APIS_JSON_META = """
    {"aid": "APIs.json"}""";

    private static byte[] apisJson;

    @Override
    public void init() throws JsonProcessingException {
        synchronized(this) {
            JsonNode apisJsonMeta = om.readTree(APIS_JSON_META);

            List<JsonNode> apis = router.getRuleManager().getRules().stream()
                    .filter(APIProxy.class::isInstance)
                    .map(r -> jsonNodeFromApiProxy((APIProxy) r)).toList();

            ObjectNode apisJsonNode = om.createObjectNode();
            apisJsonNode.set("meta", apisJsonMeta);
            apisJsonNode.putArray("apis").addAll(apis);
            apisJson = om.writeValueAsBytes(apisJsonNode);
        }
    }

    public static JsonNode jsonNodeFromApiProxy(APIProxy api) {
        ObjectNode apiJson = om.createObjectNode();
        apiJson.put("name", api.getName());
        return apiJson;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setResponse(new ResponseBuilder().body(apisJson).build());
        return CONTINUE;
    }

    @Override
    public String getDisplayName() {
        return "APIs Json";
    }

    @Override
    public String getShortDescription() {
        return "Displays all deployed API Proxies in APIs.json format";
    }

    @Override
    public String getLongDescription() {
        return "WIP"; // TODO Add long description
    }
}