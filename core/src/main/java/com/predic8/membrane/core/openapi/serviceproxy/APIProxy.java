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
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.Spec.YesNoOpenAPIOption.*;

/**
 * @description The APIProxy extends the serviceProxy with API related functions like OpenAPI support.
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
    public static final String VALIDATION_DETAILS = "details";

    protected Map<String,OpenAPIRecord> apiRecords = new LinkedHashMap<>();

    protected Map<String, OpenAPIRecord> basePaths;

    protected ValidationStatisticsCollector statisticCollector = new ValidationStatisticsCollector();

    public APIProxy() {}

    @Override
    protected AbstractProxy getNewInstance() {
        return new APIProxy();
    }

    /**
     * @description Reads an OpenAPI description and deploys an API with the information of it.
     */
    @MCElement(name = "openapi", topLevel = false)
    public static class Spec {

        String location;
        String dir;
        YesNoOpenAPIOption validateRequests = ASINOPENAPI;
        YesNoOpenAPIOption validateResponses = ASINOPENAPI;
        YesNoOpenAPIOption validationDetails = ASINOPENAPI;

        public Spec() {
        }

        public String getLocation() {
            return location;
        }

        /**
         * @description Filename or URL pointing to an OpenAPI document. Relative filenames use the %MEMBRANE_HOME%/conf folder as base directory.
         * @example openapi/fruitstore-v1.yaml, <a href="https://api.predic8.de/shop/swagger">https://api.predic8.de/shop/swagger</a>
         */
        @MCAttribute()
        public void setLocation(String location) {
            this.location = location;
        }

        public String getDir() {
            return dir;
        }

        /**
         * @description Directory containing OpenAPI definitions to deploy.
         * @example openapi
         */
        @MCAttribute()
        public void setDir(String dir) {
            this.dir = dir;
        }

        public YesNoOpenAPIOption getValidateRequests() {
            return validateRequests;
        }

        /**
         * @description Turn validation of requests on or off.
         * @example yes
         * @default no
         */
        @MCAttribute
        public void setValidateRequests(YesNoOpenAPIOption validateRequests) {
            this.validateRequests = validateRequests;
        }

        @SuppressWarnings("unused")
        public YesNoOpenAPIOption getValidateResponses() {
            return validateResponses;
        }

        /**
         * @description Turn validation of responses on or off.
         * @example yes
         * @default no
         */
        @MCAttribute()
        public void setValidateResponses(YesNoOpenAPIOption validateResponses) {
            this.validateResponses = validateResponses;
        }

        /**
         * @description Show details of the validation to the caller.
         * @example yes
         * @default no
         */
        @MCAttribute()
        public void setValidationDetails(YesNoOpenAPIOption validationDetails) {
            this.validationDetails = validationDetails;
        }

        public YesNoOpenAPIOption getValidationDetails() {
            return validationDetails;
        }

        public enum YesNoOpenAPIOption {
            YES,
            NO,
            ASINOPENAPI
        }
    }

    protected List<Spec> specs = new ArrayList<>();

    public List<Spec> getSpecs() {
        return specs;
    }

    /**
     * @description Deploys an API from an OpenAPI document.
     */
    @MCChildElement(order = 25)
    public void setSpecs(List<Spec> specs) {
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

        basePaths = getOpenAPIMap();
        configureBasePaths();

        interceptors.add(new OpenAPIPublisherInterceptor(apiRecords));
        interceptors.add(new OpenAPIInterceptor(this));
    }

    // TODO Stimmen die Pfade?
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