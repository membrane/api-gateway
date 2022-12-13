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

import java.io.*;
import java.util.*;

import static java.util.Collections.singletonList;

public class TestUtils {

    public static final ObjectMapper om = new ObjectMapper();

    public static InputStream toInputStrom(String s) {
        return new ByteArrayInputStream(s.getBytes());
    }

    public static InputStream getResourceAsStream(Object obj, String fileName) {
        return obj.getClass().getResourceAsStream(fileName);
    }

    public static OpenAPIProxy createProxy(Router router, OpenAPIProxy.Spec spec) throws Exception {
        OpenAPIProxy proxy = new OpenAPIProxy();
        proxy.init(router);
        proxy.setSpecs(singletonList(spec));
        proxy.init();
        return proxy;
    }

    @SuppressWarnings("rawtypes")
    public static Map getMapFromResponse(Exchange exc) throws IOException {
        return om.readValue(exc.getResponse().getBody().getContent(), Map.class);
    }

    public static JsonNode getJsonFromResponse(Exchange exc) throws IOException {
        return om.readValue(exc.getResponse().getBody().getContent(), JsonNode.class);
    }

    public static OpenAPIRecord getSingleOpenAPIRecord(Map<String,OpenAPIRecord> m) {
        return (OpenAPIRecord) m.values().toArray()[0];
    }
}
