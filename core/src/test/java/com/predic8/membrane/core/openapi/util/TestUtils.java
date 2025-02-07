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
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import io.swagger.parser.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.parser.*;
import io.swagger.v3.parser.core.models.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.util.FileUtil.*;
import static java.util.Collections.*;

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

    public static InputStream getResourceAsStream(Object obj, String fileName) {
        return obj.getClass().getResourceAsStream(fileName);
    }

    public static APIProxy createProxy(Router router, OpenAPISpec spec) {
        APIProxy proxy = new APIProxy();
        proxy.setSpecs(singletonList(spec));
        proxy.setKey(new APIProxyKey(2000));
        proxy.init(router);
        return proxy;
    }

    public static Map<String,Map<String,Object>> getMapFromResponse(Exchange exc) throws IOException {
        return om.readValue(exc.getResponse().getBody().getContent(), Map.class);
    }

    public static OpenAPIRecord getSingleOpenAPIRecord(Map<String,OpenAPIRecord> m) {
        return (OpenAPIRecord) m.values().toArray()[0];
    }
}