package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.spring.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.rules.*;
import io.swagger.parser.*;
import io.swagger.v3.oas.models.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.openapi.util.Utils.getPathFromURL;
import static com.predic8.membrane.core.util.FileUtil.readInputStream;
import static java.lang.String.format;

@MCElement(name = "OpenAPIProxy")
public class OpenAPIProxy extends AbstractServiceProxy {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIProxy.class.getName());

    protected List<OpenAPI> apis = new ArrayList<>();
    protected Map<String, OpenAPI> basePaths;

    public OpenAPIProxy() {
        key = new OpenAPIProxyServiceKey(4000);
    }

    ValidationStatisticsCollector statisticCollector = new ValidationStatisticsCollector();

    @Override
    protected AbstractProxy getNewInstance() {
        log.info("OpenAPIProxy.getNewInstance");
        return null;
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

        readSpecs();

        basePaths = getOpenAPIMap();
        configureBasePaths(basePaths);

        interceptors.add(new OpenAPIAPIInterceptor(apis));
        interceptors.add(new OpenAPIInterceptor(this));
    }

    private void configureBasePaths(Map<String, OpenAPI> basePaths) {
        ArrayList<String> basePathsThatMatch = new ArrayList<>(basePaths.keySet());
        basePathsThatMatch.add("/openapi/");
        ((OpenAPIProxyServiceKey) key).setBasePaths(basePathsThatMatch);
    }

    private void readSpecs() throws ResourceRetrievalException, FileNotFoundException {
        for (Spec spec : specs) {
            if (spec.location != null) {
                log.info("Parsing spec " + spec.location);
                apis.add(addLocationAsOpenAPI(spec));
            }
            if (spec.dir != null) {
                log.info("Parsing specs from dir " + spec.dir);
                addOpenAPISpecsFromDirectory(spec);
            }
        }
    }

    private void addOpenAPISpecsFromDirectory(Spec spec) throws FileNotFoundException {
        for (File file : getOpenAPIFiles(spec.dir)) {
            log.info("Parsing spec " + file);
            OpenAPI api = parseFileAsOpenAPI(file);
            setExtentsionOnAPI(spec, api);
            apis.add(api);
        }
    }

    private OpenAPI parseFileAsOpenAPI(File oaFile) throws FileNotFoundException {
        return new OpenAPIParser().readContents(readInputStream(new FileInputStream(oaFile)),
                null, null).getOpenAPI();
    }

    private OpenAPI addLocationAsOpenAPI(Spec spec) throws ResourceRetrievalException {
        OpenAPI api = new OpenAPIParser().readContents(readInputStream(getInputStreamForLocation(spec.location)),
                null, null).getOpenAPI();

        if (api.getExtensions() == null) {
            api.setExtensions(new HashMap<>());
        }

        setExtentsionOnAPI(spec, api);
        return api;
    }

    private void setExtentsionOnAPI(Spec spec, OpenAPI api) {
        api.getExtensions().put("x-validation", updateExtension(getXValidationExtension(api), spec));
    }

    private Map<String, Object> getXValidationExtension(OpenAPI api) {
        if (api.getExtensions().get("x-validation") != null)
            //noinspection unchecked
            return (Map<String, Object>) api.getExtensions().get("x-validation");

        Map<String, Object> extension = new HashMap<>();
        extension.put("requests", false);
        extension.put("responses", false);
        extension.put("validationDetails", true);
        return extension;
    }

    private Map<String, Object> updateExtension(Map<String, Object> extension, Spec spec) {

        if (spec.validationDetails != null)
            extension.put("validationDetails", spec.validationDetails);

        if (spec.validateRequests != null)
            extension.put("requests", spec.validateRequests);

        if (spec.validateResponses != null)
            extension.put("responses", spec.validateResponses);

        extension.putIfAbsent("requests", false);
        extension.putIfAbsent("responses", false);

        return extension;
    }

    private Map<String, OpenAPI> getOpenAPIMap() {
        Map<String, OpenAPI> basePaths = new HashMap<>();
        apis.forEach(api -> api.getServers().forEach(server -> {
            try {
                basePaths.put(getPathFromURL(server.getUrl()), api);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                // @TODO
                throw new RuntimeException();
            }
        }));
        return basePaths;
    }

    private File[] getOpenAPIFiles(String dir) {
        return new File(dir).listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    public ValidationStatisticsCollector getValidationStatisticCollector() {
        return statisticCollector;
    }

    private InputStream getInputStreamForLocation(String location) throws ResourceRetrievalException {
        return router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), location));
    }

    public enum VALIDATE_OPTIONS {
        all, requests, responses, none;

        public static List<String> getValues() {
            return Stream.of(values())
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }
    }

    public Map<String, OpenAPI> getBasePaths() {
        return basePaths;
    }
}
