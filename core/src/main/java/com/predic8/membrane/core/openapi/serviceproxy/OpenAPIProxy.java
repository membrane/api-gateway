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

import static com.predic8.membrane.core.util.FileUtil.readInputStream;
import static java.lang.String.format;

@MCElement(name="OpenAPIProxy")
public class OpenAPIProxy extends AbstractServiceProxy {

    private static Logger log = LoggerFactory.getLogger(OpenAPIProxy.class.getName());

    private String dir;
    private List<OpenAPI> apis = new ArrayList<>();

    public OpenAPIProxy() {
        key = new OpenAPIProxyServiceKey(4000);
    }

    ValidationStatisticsCollector statisticCollector = new ValidationStatisticsCollector();

    @Override
    protected AbstractProxy getNewInstance() {
        log.info("OpenAPIProxy.getNewInstance");
        return null;
    }

    @MCElement(name="spec",topLevel = false)
    public static class Spec {

        String location;
        String dir;
        String validate = "false";

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

        public String getValidate() {
            return validate;
        }

        @MCAttribute()
        public void setValidate(String validate) {
            this.validate = validate;
        }
    }

    protected List<Spec> specs = new ArrayList<>();

    public List<Spec> getSpecs() {
        return specs;
    }

    @MCChildElement(order=25)
    public void setSpecs(List<Spec> specs) {
        this.specs = specs;
    }

    @Override
    public void init() throws Exception {
        super.init();

        readSpecs();

        Map<String,OpenAPI>  basePaths = getOpenAPIMap();
        configureBasePaths(basePaths);

        interceptors.add(new OpenAPIAPIInterceptor(apis));
        interceptors.add(new OpenAPIInterceptor(basePaths, this));
    }

    private void configureBasePaths(Map<String, OpenAPI> basePaths) {
        ArrayList<String> basePathsThatMatch = new ArrayList<>(basePaths.keySet());
        basePathsThatMatch.add("/openapi/");
        ((OpenAPIProxyServiceKey)key).setBasePaths(basePathsThatMatch);
    }

    private void readSpecs() throws ResourceRetrievalException, FileNotFoundException, CheckableBeanFactory.InvalidConfigurationException {
        for (Spec spec: specs) {
            if (spec.location != null) {
                log.info("Parsing spec " + spec.location);
                apis.add(parseLocationAsOpenAPI(spec));
            }
            if (spec.dir != null) {
                log.info("Parsing specs from dir " + spec.dir);
                addOpenAPISpecsFromDirectory(spec);
            }
        }
    }

    private void addOpenAPISpecsFromDirectory(Spec spec) throws FileNotFoundException, CheckableBeanFactory.InvalidConfigurationException {
        for (File file : getOpenAPIFiles(spec.dir)) {
            log.info("Parsing spec " + file);
            OpenAPI api = parseFileAsOpenAPI(file);
            setValidationOnAPI(spec, api);
            apis.add(api);
        }
    }

    private OpenAPI parseFileAsOpenAPI(File oaFile) throws FileNotFoundException {
        return new OpenAPIParser().readContents(readInputStream(new FileInputStream(oaFile)),
                null, null).getOpenAPI();
    }

    private OpenAPI parseLocationAsOpenAPI(Spec spec) throws ResourceRetrievalException, CheckableBeanFactory.InvalidConfigurationException {
        OpenAPI api = new OpenAPIParser().readContents(readInputStream(getInputStreamForLocation(spec.location)),
                        null, null).getOpenAPI();
        setValidationOnAPI(spec, api);
        return api;
    }

    private void setValidationOnAPI(Spec spec, OpenAPI api) throws CheckableBeanFactory.InvalidConfigurationException {
        if (!isValidationOptionCorrect(spec)) {
            throw new CheckableBeanFactory.InvalidConfigurationException(format("The value %s is not a valid option for the validation attribute. Please use a value of %s", spec.validate, VALIDATE_OPTIONS.getValues() ));
        }

        if (api.getExtensions() == null) {
            api.setExtensions(new HashMap<>());
        }

        api.getExtensions().put("x-validation", getValidationOptions(spec));
    }

    private Map<String,Boolean> getValidationOptions(Spec spec) {
        Map<String,Boolean> validationOptions = new HashMap<>();
        validationOptions.put("requests",false);
        validationOptions.put("responses",false);
        switch (spec.validate) {
            case "all":
                validationOptions.put("requests",true);
                validationOptions.put("responses",true);
                break;
            case "requests":
                validationOptions.put("requests",true);
                break;
            case "responses":
                validationOptions.put("responses",true);
                break;
            default:
        }
        return validationOptions;
    }

    private boolean isValidationOptionCorrect(Spec spec) {
        return Arrays.stream(VALIDATE_OPTIONS.values()).anyMatch(o -> o.name().equalsIgnoreCase(spec.validate));
    }

    private Map<String,OpenAPI> getOpenAPIMap() {
        Map<String,OpenAPI> basePaths = new HashMap<>();
        apis.forEach(api -> api.getServers().forEach(server -> {
            try {
                basePaths.put(Utils.getPathFromURL(server.getUrl()), api);
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

    public String getDir() {
        return dir;
    }

    @MCAttribute
    public void setDir(String dir) {
        this.dir = dir;
    }

    public ValidationStatisticsCollector getValidationStatisticCollector() {
        return statisticCollector;
    }

    private InputStream getInputStreamForLocation(String location) throws ResourceRetrievalException {
        return router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(),  location));
    }

    public enum VALIDATE_OPTIONS { all, requests, responses, none;

        public static List<String> getValues() {
            return Stream.of(values())
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }
    }
}
