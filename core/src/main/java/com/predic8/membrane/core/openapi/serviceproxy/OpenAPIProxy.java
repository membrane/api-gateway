package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.rules.*;
import io.swagger.v3.oas.models.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;

@MCElement(name = "OpenAPIProxy")
public class OpenAPIProxy extends ServiceProxy {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIProxy.class.getName());

    public static final String X_MEMBRANE_VALIDATION = "x-membrane-validation";
    public static final String REQUESTS = "requests";
    public static final String RESPONSES = "responses";
    public static final String VALIDATION_DETAILS = "validationDetails";

    protected List<OpenAPIRecord> apiRecords = new ArrayList<>();

    protected Map<String, OpenAPI> basePaths;

    public OpenAPIProxy() {
        key = new OpenAPIProxyServiceKey(4000);
    }

    ValidationStatisticsCollector statisticCollector = new ValidationStatisticsCollector();

    @Override
    protected AbstractProxy getNewInstance() {
        return new OpenAPIProxy();
    }

    @MCElement(name = "spec", topLevel = false)
    public static class Spec {

        String location;
        String dir;
        Boolean validateRequests;
        Boolean validateResponses;
        Boolean validationDetails;

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

        public boolean getValidateRequests() {
            return validateRequests;
        }

        @MCAttribute()
        public void setValidateRequests(boolean validateRequests) {
            this.validateRequests = validateRequests;
        }

        public boolean getValidateResponses() {
            return validateResponses;
        }

        @MCAttribute()
        public void setValidateResponses(boolean validateResponses) {
            this.validateResponses = validateResponses;
        }

        public boolean getValidationDetails() {
            return validationDetails;
        }

        @MCAttribute()
        public void setValidationDetails(boolean validationDetails) {
            this.validationDetails = validationDetails;
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
        interceptors.add(new OpenAPIAPIInterceptor(apiRecords));
        interceptors.add(new OpenAPIInterceptor(this));
    }

    private void configureBasePaths() {
        ArrayList<String> basePathsThatMatch = new ArrayList<>(basePaths.keySet());
        basePathsThatMatch.add("/openapi/"); // Add path for the API for OpenAPI
        basePathsThatMatch.add("/openapi-spec");
        ((OpenAPIProxyServiceKey) key).setBasePaths(basePathsThatMatch);
    }

    private Map<String, OpenAPI> getOpenAPIMap() {
        Map<String, OpenAPI> basePaths = new HashMap<>();
        apiRecords.forEach(record -> record.api.getServers().forEach(server -> {
            try {
                basePaths.put(getPathFromURL(server.getUrl()), record.api);
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