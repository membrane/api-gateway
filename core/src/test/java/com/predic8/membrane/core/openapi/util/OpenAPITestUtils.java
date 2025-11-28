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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.datatype.joda.JodaModule;

import java.io.*;
import java.util.Map;

import static com.predic8.membrane.core.util.FileUtil.readInputStream;
import static java.util.Collections.singletonList;

public class OpenAPITestUtils {

    public static final ObjectMapper om = new ObjectMapper();
    private static final ObjectMapper omYaml =  YAMLMapper.builder().addModule(new JodaModule()).build();

    public static InputStream toInputStrom(String s) {
        return new ByteArrayInputStream(s.getBytes());
    }

    /**
     * @param thisObj this of the caller
     * @param path    path into src/resources
     * @return YAML as JsonNode
     * @throws IOException Can not read resource
     */
    public static JsonNode getYAMLResource(Object thisObj, String path) throws IOException {
        try (InputStream is = getResourceAsStream(thisObj, path)) {
            if (is == null) throw new FileNotFoundException("Resource not found: " + path);
            return omYaml.readTree(is);
        }
    }

    public static OpenAPI parseOpenAPI(InputStream is) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        return new OpenAPIParser().readContents(readInputStream(is), null, parseOptions).getOpenAPI();
    }

    public static OpenAPI parseOpenAPI(String file) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        return new OpenAPIParser().readLocation(file, null, parseOptions).getOpenAPI();
    }


    public static APIProxy createProxy(Router router, OpenAPISpec spec) {
        APIProxy proxy = new APIProxy();
        proxy.setSpecs(singletonList(spec));
        proxy.setKey(new APIProxyKey(2000));
        proxy.init(router);
        return proxy;
    }

    public static Map<String, Map<String, Object>> getMapFromResponse(Exchange exc) throws IOException {
        return om.readValue(exc.getResponse().getBody().getContent(), Map.class);
    }

    public static OpenAPIRecord getSingleOpenAPIRecord(Map<String, OpenAPIRecord> m) {
        return m.values().iterator().next();
    }

    public static OpenAPI getApi(Object obj, String path) {
        ParseOptions opts = new ParseOptions();
        opts.setResolve(true);
        try (InputStream is = getResourceAsStream(obj, path)) {
            return new OpenAPIParser().readContents(readInputStream(is), null, opts).getOpenAPI();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static InputStream getResourceAsStream(Object obj, String fileName) {
        return obj.getClass().getResourceAsStream(fileName);
    }
}