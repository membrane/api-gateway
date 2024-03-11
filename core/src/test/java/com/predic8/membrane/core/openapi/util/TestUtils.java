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
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import io.swagger.v3.parser.ObjectMapperFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static java.util.Collections.singletonList;

public class TestUtils {

    public static final ObjectMapper om = new ObjectMapper();
    private static final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    public static InputStream toInputStrom(String s) {
        return new ByteArrayInputStream(s.getBytes());
    }

    /**
     *
     * @param thisObj this of the caller
     * @param path path into src/resources
     * @return YAML as JsonNode
     * @throws IOException Can not read resource
     */
    public static JsonNode getYAMLResource(Object thisObj,String path) throws IOException {
        return omYaml.readTree(getResourceAsStream(thisObj,path));
    }

    public static InputStream getResourceAsStream(Object obj, String fileName) {
        return obj.getClass().getResourceAsStream(fileName);
    }

    public static APIProxy createProxy(Router router, OpenAPISpec spec) throws Exception {
        APIProxy proxy = new APIProxy();
        proxy.init(router);
        proxy.setSpecs(singletonList(spec));
        proxy.setKey(new ProxyRuleKey(2000));
        proxy.init();
        return proxy;
    }

    @SuppressWarnings("rawtypes")
    public static Map getMapFromResponse(Exchange exc) throws IOException {
        byte[] content = exc.getResponse().getBody().getContent();
        return om.readValue(content, Map.class);
    }

    public static OpenAPIRecord getSingleOpenAPIRecord(Map<String,OpenAPIRecord> m) {
        return (OpenAPIRecord) m.values().toArray()[0];
    }
}