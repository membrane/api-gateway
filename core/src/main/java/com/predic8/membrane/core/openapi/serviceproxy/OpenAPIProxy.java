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
import io.swagger.v3.oas.models.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;

@MCElement(name = "openAPIProxy")
public class OpenAPIProxy extends ServiceProxy {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIProxy.class.getName());

    public static final String X_MEMBRANE_VALIDATION = "x-membrane-validation";
    public static final String X_MEMBRANE_ID = "x-membrane-id";
    public static final String REQUESTS = "requests";
    public static final String RESPONSES = "responses";
    public static final String VALIDATION_DETAILS = "validationDetails";


    protected Map<String,OpenAPIRecord> apiRecords = new LinkedHashMap<>();

    protected Map<String, OpenAPI> basePaths;

    protected ValidationStatisticsCollector statisticCollector = new ValidationStatisticsCollector();

    public OpenAPIProxy() {
        key = new OpenAPIProxyServiceKey(getPort());
    }

    @Override
    protected AbstractProxy getNewInstance() {
        return new OpenAPIProxy();
    }

    @MCElement(name = "spec", topLevel = false)
    public static class Spec {

        String location;
        String dir;
        YesNoOpenAPIOption validateRequests = YesNoOpenAPIOption.ASINOPENAPI;
        YesNoOpenAPIOption validateResponses = YesNoOpenAPIOption.ASINOPENAPI;
        YesNoOpenAPIOption validationDetails = YesNoOpenAPIOption.ASINOPENAPI;

        public Spec() {
        }

        public String getLocation() {
            return location;
        }

        @MCAttribute()
        public void setLocation(String location) {
            this.location = location;
        }

        public String getDir() {
            return dir;
        }

        @MCAttribute()
        public void setDir(String dir) {
            this.dir = dir;
        }

        public YesNoOpenAPIOption getValidateRequests() {
            return validateRequests;
        }

        @MCAttribute
        public void setValidateRequests(YesNoOpenAPIOption validateRequests) {
            this.validateRequests = validateRequests;
        }

        public YesNoOpenAPIOption getValidateResponses() {
            return validateResponses;
        }

        @MCAttribute()
        public void setValidateResponses(YesNoOpenAPIOption validateResponses) {
            this.validateResponses = validateResponses;
        }

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

    @MCChildElement(order = 25)
    public void setSpecs(List<Spec> specs) {
        this.specs = specs;
    }

    @Override
    public void init() throws Exception {
        super.init();

        apiRecords = new OpenAPIRecordFactory(router).create(specs);

        basePaths = getOpenAPIMap();
        configureBasePaths();

        interceptors.add(new OpenAPIPublisherInterceptor(apiRecords));
        //interceptors.add(new OpenAPIAPIInterceptor(apiRecords));
        interceptors.add(new OpenAPIInterceptor(this));
    }

    // TODO Stimmen die Pfade?
    private void configureBasePaths() {
        ArrayList<String> basePathsThatMatch = new ArrayList<>(basePaths.keySet());
        basePathsThatMatch.add("/openapi/"); // Add path for the API for OpenAPI
        basePathsThatMatch.add("/openapi-spec");
        ((OpenAPIProxyServiceKey) key).setBasePaths(basePathsThatMatch);
    }

    private Map<String, OpenAPI> getOpenAPIMap() {
        Map<String, OpenAPI> basePaths = new HashMap<>();
        apiRecords.forEach((id,record) -> record.api.getServers().forEach(server -> {
            try {
                basePaths.put(UriUtil.getPathFromURL(server.getUrl()), record.api);
            } catch (MalformedURLException e) {
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

    public Map<String, OpenAPI> getBasePaths() {
        return basePaths;
    }
}