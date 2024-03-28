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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.URI;
import io.swagger.v3.oas.models.servers.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * @description The api proxy extends the serviceProxy with API related functions like OpenAPI support.
 *
 * @topic 2. Proxies
 */
@MCElement(name = "api")
public class APIProxy extends ServiceProxy {

    private static final Logger log = LoggerFactory.getLogger(APIProxy.class.getName());

    public static final String X_MEMBRANE_VALIDATION = "x-membrane-validation";
    public static final String X_MEMBRANE_ID = "x-membrane-id";
    public static final String REQUESTS = "requests";
    public static final String RESPONSES = "responses";
    public static final String SECURITY = "security";
    public static final String VALIDATION_DETAILS = "details";

    protected Map<String,OpenAPIRecord> apiRecords = new LinkedHashMap<>();

    protected Map<String, OpenAPIRecord> basePaths;

    protected ValidationStatisticsCollector statisticCollector = new ValidationStatisticsCollector();

    public APIProxy() {}

    @Override
    protected AbstractProxy getNewInstance() {
        return new APIProxy();
    }


    protected List<OpenAPISpec> specs = new ArrayList<>();

    public List<OpenAPISpec> getSpecs() {
        return specs;
    }

    /**
     * @description Deploys an API from an OpenAPI document.
     */
    @MCChildElement(order = 25)
    public void setSpecs(List<OpenAPISpec> specs) {
        this.specs = specs;
    }

    @Override
    public void init() throws Exception {
        if (specs.size() > 0)
            key = new OpenAPIProxyServiceKey(getIp(),getHost(),getPort()); // Must come before super.  init()
        super.init();
        initOpenAPI();
    }

    private void initOpenAPI() throws IOException, ClassNotFoundException {
        if (specs.size() == 0)
            return;

        apiRecords = new OpenAPIRecordFactory(router).create(specs);

        checkForDuplicatePaths();

        basePaths = getOpenAPIMap();
        configureBasePaths();

        interceptors.add(new OpenAPIPublisherInterceptor(apiRecords));
        interceptors.add(new OpenAPIInterceptor(this));
    }

    /**
     * One API should not have multiple OpenAPI specs sharing the same path. The interceptor needs the path to check against
     * the right OpenAPI. Therefor the path must be unique.
     * The check does not consider variables. Maybe this is needed in the future?
     */
    void checkForDuplicatePaths() {

        Map<String,List<OpenAPIRecord>> paths = new HashMap<>();

        apiRecords.values().forEach(rec -> {
            for (Server server : rec.api.getServers()) {
                URI uri;
                try {
                    uri = new URIFactory(true).create(server.getUrl());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }

                String path = uri.getPath();

                if (paths.containsKey(path)) {
                    List<OpenAPIRecord> l = paths.get(path);
                    // Check if the path is not from the same API. One OpenAPI can have several server.urls with the same path.
                    if (!l.contains(rec)) {
                        log.error("Several OpenAPI documents of the API {} share the same path {}. Make sure that the values of info.servers.url in the OpenAPI documents are unique.", name, path);
                        throw new DuplicatePathException(path);
                    }
                }

                paths.computeIfAbsent(path, s -> {
                    List<OpenAPIRecord> apis = new ArrayList<>();
                    apis.add(rec);
                    return apis;
                });
            }
        });
    }

    private void configureBasePaths() {
        ((OpenAPIProxyServiceKey) key).addBasePaths(new ArrayList<>(basePaths.keySet()));
    }

    private Map<String, OpenAPIRecord> getOpenAPIMap() {
        Map<String, OpenAPIRecord> basePaths = new HashMap<>();
        apiRecords.forEach((id,record) -> record.api.getServers().forEach(server -> {
            try {
                basePaths.put(UriUtil.getPathFromURL(router.getUriFactory(),server.getUrl()), record);
            } catch (URISyntaxException e) {
                e.printStackTrace();
                // @TODO
                throw new RuntimeException();
            }
        }));
        return basePaths;
    }

    public ValidationStatisticsCollector getValidationStatisticCollector() {
        return statisticCollector;
    }

    public Map<String, OpenAPIRecord> getBasePaths() {
        return basePaths;
    }
}