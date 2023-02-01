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
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIProxy.Spec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.util.FileUtil.*;

public class OpenAPIRecordFactory {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIRecordFactory.class.getName());

    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    private Router router;

    public OpenAPIRecordFactory(Router router) {
        this.router = router;
    }

    public Map<String, OpenAPIRecord> create(Collection<Spec> specs) throws IOException {
        Map<String, OpenAPIRecord> apiRecords = new LinkedHashMap<>();
        for (Spec spec : specs) {
            // Maybe a spec has both location and dir.
            addOpenApisFromLocation(apiRecords, spec);
            addOpenApisFromDirectory(apiRecords, spec);
        }
        return apiRecords;
    }

    private void addOpenApisFromDirectory(Map<String, OpenAPIRecord> apiRecords, Spec spec) throws IOException {
        if (spec.dir == null)
            return;

        log.info("Parsing specs from dir " + spec.dir);
        File[] openAPIFiles = getOpenAPIFiles(spec.dir);
        if (openAPIFiles == null) {
            log.warn(String.format("Directory %s does not contain any OpenAPI documents.", spec.dir));
            return;
        }
        for (File file : openAPIFiles) {
            log.info("Parsing spec " + file);
            OpenAPIRecord rec = create(spec, file);
            apiRecords.put(getUniqueId(apiRecords,rec), rec);
        }
    }

    private void addOpenApisFromLocation(Map<String, OpenAPIRecord> apiRecords, Spec spec) throws IOException {
        if (spec.location == null)
            return;

        log.info("Parsing spec " + spec.location);
        apiRecords.put(getUniqueId(apiRecords, create(spec)), create(spec));
    }

    private String getUniqueId(Map<String, OpenAPIRecord> apiRecords, OpenAPIRecord rec) {
        String id = getIdFromAPI(rec.api);
        if (apiRecords.get(id) != null) {
            log.warn("There are multiple OpenAPI documents with the id {}. The id is computed from the title {} and version {}. Please make sure that the documents are different or use the x-membrane-id field.",
                    id, rec.api.getInfo().getTitle(), rec.api.getInfo().getVersion());
            id += "-0";
            log.warn("Changing the id to {} in order to make them unique.", id);
        }
        return id;
    }

    private OpenAPIRecord create(Spec spec) throws IOException {
        OpenAPIRecord record = new OpenAPIRecord(getOpenAPI(router, spec), getSpec(router, spec));
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

        if (spec.validationDetails != ASINOPENAPI)
            extension.put(VALIDATION_DETAILS, toYesNo(spec.validationDetails));

        if (spec.validateRequests != ASINOPENAPI)
            extension.put(REQUESTS, toYesNo(spec.validateRequests));

        if (spec.validateResponses != ASINOPENAPI)
            extension.put(RESPONSES, toYesNo(spec.validateResponses));

        extension.putIfAbsent(REQUESTS, false);
        extension.putIfAbsent(RESPONSES, false);

        return extension;
    }

    private boolean toYesNo(Spec.YesNoOpenAPIOption option) {
        return option == YES;
    }

    private File[] getOpenAPIFiles(String dir) {
        File file = new File(dir);
        log.info("Reading from folder: " + file.getAbsolutePath());
        return file.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json"));
    }
}
