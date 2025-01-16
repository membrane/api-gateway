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
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.*;
import io.swagger.v3.oas.models.servers.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;

/**
 * @description The api proxy extends the serviceProxy with API related functions like OpenAPI support.
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

    private String test;
    private String id;
    private ApiDescription description;

    protected Map<String, OpenAPIRecord> apiRecords = new LinkedHashMap<>();

    protected Map<String, OpenAPIRecord> basePaths;

    protected final ValidationStatisticsCollector statisticCollector = new ValidationStatisticsCollector();

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
    public void init() {
        super.init();
        key = new APIProxyKey(key, test, !specs.isEmpty());
        initOpenAPI();
    }

    private void initOpenAPI() {
        if (specs.isEmpty())
            return;

        apiRecords = new OpenAPIRecordFactory(router).create(specs);

        checkForDuplicatePaths();

        basePaths = getOpenAPIMap();
        configureBasePaths();

        interceptors.add(new OpenAPIPublisherInterceptor(apiRecords));
        interceptors.add(new OpenAPIInterceptor(this, router));
    }

    /**
     * One API should not have multiple OpenAPI specs sharing the same path. The interceptor needs the path to check against
     * the right OpenAPI. Therefor the path must be unique.
     * The check does not consider variables. Maybe this is needed in the future?
     */
    void checkForDuplicatePaths() {

        Map<String, List<OpenAPIRecord>> paths = new HashMap<>();

        apiRecords.values().forEach(rec -> {
            for (Server server : rec.api.getServers()) {
                String path = getUriPath(server);
                if (paths.containsKey(path)) {
                    List<OpenAPIRecord> l = paths.get(path);
                    // Check if the path is not from the same API. One OpenAPI can have several server.urls with the same path.
                    if (!l.contains(rec)) {
                        throw new ConfigurationException("""
                            ================================================================================================
        
                            Configuration Error: Several OpenAPI Documents share the same path!
        
                            An API routes and validates requests according to the path of the OpenAPI's servers.url fields.
                            Within one API the same path should be used only by one OpenAPI. Change the paths or place
                            openapi-elements into separate APIs.
        
                            Shared path: %s
                            %n""".formatted(path));
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

    private static String getUriPath(Server server) {
        try {
            return new URIFactory(true).create(server.getUrl()).getPath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void configureBasePaths() {
        ((APIProxyKey) key).addBasePaths(new ArrayList<>(basePaths.keySet()));
    }

    private Map<String, OpenAPIRecord> getOpenAPIMap() {
        Map<String, OpenAPIRecord> paths = new HashMap<>();
        apiRecords.forEach((id, rec) -> rec.api.getServers().forEach(server -> {
            String url = server.getUrl();
            if (rec.spec.getRewrite() != null && rec.spec.getRewrite().basePath != null) {
                url = rec.spec.getRewrite().basePath;
            }
            try {
                paths.put(UriUtil.getPathFromURL(router.getUriFactory(), url), rec);
            } catch (URISyntaxException e) {
                log.error("Cannot parse URL {}", url);
                throw new RuntimeException("Cannot parse URL %s".formatted(url));
            }
        }));
        return paths;
    }

    public ValidationStatisticsCollector getValidationStatisticCollector() {
        return statisticCollector;
    }

    public Map<String, OpenAPIRecord> getApiRecords() {
        return apiRecords;
    }

    public Map<String, OpenAPIRecord> getBasePaths() {
        return basePaths;
    }

    public String getTest() {
        return test;
    }

    @MCAttribute
    public void setTest(String test) {
        this.test = test;
    }

    public String getId() {
        return id;
    }

    public ApiDescription getDescription() {
        return description;
    }

    @MCAttribute
    public void setId(String id) {
        this.id = id;
    }

    @MCChildElement
    public void setDescription(ApiDescription description) {
        this.description = description;
    }

    @MCElement(name = "description", topLevel = false, mixed = true)
    public static class ApiDescription {
        private String content;

        @MCTextContent
        public void setContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }
}