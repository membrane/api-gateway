package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.resolver.*;
import io.swagger.parser.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.parser.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIProxy.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.getIdFromAPI;
import static com.predic8.membrane.core.util.FileUtil.*;

public class OpenAPIRecordFactory {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIRecordFactory.class.getName());

    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    private Router router;

    public OpenAPIRecordFactory(Router router) {
        this.router = router;
    }

    public Map<String,OpenAPIRecord> create(Collection<Spec> specs) throws IOException {

        Map<String,OpenAPIRecord> apiRecords = new LinkedHashMap<>();

        for (Spec spec : specs) {
            // Maybe a spec has both location and dir.
            if (spec.location != null) {
                log.info("Parsing spec " + spec.location);
                OpenAPIRecord rec = create(spec);

                // TODO
                String id = getIdFromAPI(rec.api);
                if (apiRecords.get(id) != null) {
                    System.out.println("Duplicate id = " + id);
                    id += "-2";
                }
                apiRecords.put(id,rec);
            }
            if (spec.dir != null) {
                log.info("Parsing specs from dir " + spec.dir);
                File[] openAPIFiles = getOpenAPIFiles(spec.dir);
                if (openAPIFiles == null) {
                    log.warn(String.format("Directory %s does not contain any OpenAPI documents.",spec.dir));
                    continue;
                }
                for (File file : openAPIFiles) {
                    log.info("Parsing spec " + file);
                    OpenAPIRecord rec =create(spec,file);

                    // TODO
                    String id = getIdFromAPI(rec.api);
                    if (apiRecords.get(id) != null) {
                        System.out.println("Duplicate id = " + id);
                        id += "-2";
                    }
                    apiRecords.put(id, rec);
                }
            }
        }
        return apiRecords;
    }

    private OpenAPIRecord create(Spec spec) throws IOException {
        OpenAPIRecord record = new OpenAPIRecord(getOpenAPI(router,spec), getSpec(router, spec));
        setExtentsionOnAPI(spec, record.api);
        return record;
    }

    private OpenAPIRecord create(Spec spec, File file) throws IOException {
        OpenAPIRecord record = new OpenAPIRecord(parseFileAsOpenAPI(file), getSpec(file));
        setExtentsionOnAPI(spec, record.api);
        return record;
    }

    private OpenAPI getOpenAPI(Router router, Spec spec) throws ResourceRetrievalException {
        return new OpenAPIParser().readContents(readInputStream(getInputStreamForLocation(router, spec.location)),
                null, null).getOpenAPI();
    }

    private OpenAPI parseFileAsOpenAPI(File oaFile) throws FileNotFoundException {
        return new OpenAPIParser().readContents(readInputStream(new FileInputStream(oaFile)),
                null, null).getOpenAPI();
    }

    private InputStream getInputStreamForLocation(Router router, String location) throws ResourceRetrievalException {
        return router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), location));
    }

    private JsonNode getSpec(Router router, Spec spec) throws IOException {
        return omYaml.readTree(getInputStreamForLocation(router, spec.location));
    }

    private JsonNode getSpec(File file) throws IOException {
        return omYaml.readTree(file);
    }

    private void setExtentsionOnAPI(Spec spec, OpenAPI api) {
        if (api.getExtensions() == null) {
            api.setExtensions(new HashMap<>());
        }
        api.getExtensions().put(X_MEMBRANE_VALIDATION, updateExtension(getXValidationExtension(api), spec));
    }

    private Map<String, Object> getXValidationExtension(OpenAPI api) {
        if (api.getExtensions().get(X_MEMBRANE_VALIDATION) != null)
            //noinspection unchecked
            return (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        Map<String, Object> extension = new HashMap<>();
        extension.put(REQUESTS, false);
        extension.put(RESPONSES, false);
        extension.put(VALIDATION_DETAILS, true);
        return extension;
    }

    private Map<String, Object> updateExtension(Map<String, Object> extension, Spec spec) {

        if (spec.validationDetails != null)
            extension.put(VALIDATION_DETAILS, spec.validationDetails);

        if (spec.validateRequests != null)
            extension.put(REQUESTS, spec.validateRequests);

        if (spec.validateResponses != null)
            extension.put(RESPONSES, spec.validateResponses);

        extension.putIfAbsent(REQUESTS, false);
        extension.putIfAbsent(RESPONSES, false);

        return extension;
    }

    private File[] getOpenAPIFiles(String dir) {
        return new File(dir).listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
    }
}
