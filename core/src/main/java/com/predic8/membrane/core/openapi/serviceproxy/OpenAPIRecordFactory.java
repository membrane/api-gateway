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
import com.predic8.membrane.core.util.*;
import io.swagger.parser.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.parser.*;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.apache.commons.lang3.exception.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.util.FileUtil.*;
import static java.lang.String.*;

public class OpenAPIRecordFactory {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIRecordFactory.class.getName());

    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    private final Router router;

    public OpenAPIRecordFactory(Router router) {
        this.router = router;
    }

    public Map<String, OpenAPIRecord> create(Collection<OpenAPISpec> specs) throws IOException {
        Map<String, OpenAPIRecord> apiRecords = new LinkedHashMap<>();
        for (OpenAPISpec spec : specs) {
            // Maybe a spec has both location and dir.
            addOpenApisFromLocation(apiRecords, spec);
            addOpenApisFromDirectory(apiRecords, spec);
        }
        return apiRecords;
    }

    private void addOpenApisFromDirectory(Map<String, OpenAPIRecord> apiRecords, OpenAPISpec spec) throws IOException {
        if (spec.dir == null)
            return;

        log.info("Parsing specs from dir " + spec.dir);
        File[] openAPIFiles = getOpenAPIFiles(spec.dir);
        if (openAPIFiles == null) {
            log.warn(format("Directory %s does not contain any OpenAPI documents.", spec.dir));
            return;
        }
        for (File file : openAPIFiles) {
            log.info("Parsing spec " + file);
            OpenAPIRecord rec = create(spec, file);
            apiRecords.put(getUniqueId(apiRecords, rec), rec);
        }
    }

    private void addOpenApisFromLocation(Map<String, OpenAPIRecord> apiRecords, OpenAPISpec spec) {
        if (spec.location == null)
            return;

        try {
            log.info("Parsing spec " + spec.location);
            OpenAPIRecord rec = create(spec);
            apiRecords.put(getUniqueId(apiRecords, rec), rec);
        } catch (Exception e) {
            Throwable root = ExceptionUtils.getRootCause(e);
            if (root instanceof UnknownHostException) {
                throw new ConfigurationException(format("""
                        Error accessing OpenAPI specification from location: %s
                                            
                        The hostname cannot be resolved to an IP address. Maybe the internet
                        is not reachable or a proxy server configuration is needed.
                                            
                        Have a look at: ...
                            """, spec.location));
            }

            log.error("Cannot read OpenAPI specification from location " + spec.location);
            throw new ConfigurationException("Cannot read OpenAPI specification from location: " + spec.location);
        }
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

    private OpenAPIRecord create(OpenAPISpec spec) throws IOException {
        OpenAPIRecord record = new OpenAPIRecord(getOpenAPI(router, spec), getSpec(router, spec), spec);
        setExtensionOnAPI(spec, record.api);
        return record;
    }

    private OpenAPIRecord create(OpenAPISpec spec, File file) throws IOException {
        OpenAPIRecord record = new OpenAPIRecord(parseFileAsOpenAPI(file), getSpec(file), spec);
        setExtensionOnAPI(spec, record.api);
        return record;
    }

    private OpenAPI getOpenAPI(Router router, OpenAPISpec spec) throws ResourceRetrievalException {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        OpenAPI openAPI = new OpenAPIParser().readContents(readInputStream(getInputStreamForLocation(router, spec.location)),
                null, null).getOpenAPI();
        if (openAPI != null)
            return openAPI;

        throw new RuntimeException(); // Is handled and turned into a nice Exception further up
    }

    private OpenAPI parseFileAsOpenAPI(File oaFile) throws FileNotFoundException {
        return new OpenAPIParser().readContents(readInputStream(new FileInputStream(oaFile)),
                null, null).getOpenAPI();
    }

    private InputStream getInputStreamForLocation(Router router, String location) throws ResourceRetrievalException {
        return router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), location));
    }

    private JsonNode getSpec(Router router, OpenAPISpec spec) throws IOException {
        return omYaml.readTree(getInputStreamForLocation(router, spec.location));
    }

    private JsonNode getSpec(File file) throws IOException {
        return omYaml.readTree(file);
    }

    private void setExtensionOnAPI(OpenAPISpec spec, OpenAPI api) {
        if (api.getExtensions() == null) {
            api.setExtensions(new HashMap<>());
        }
        api.getExtensions().put(X_MEMBRANE_VALIDATION, updateExtension(getXValidationExtension(api), spec));
    }

    public static Map<String, Object> getXValidationExtension(OpenAPI api) {
        if (api.getExtensions().get(X_MEMBRANE_VALIDATION) != null)
            //noinspection unchecked
            return (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        Map<String, Object> extension = new HashMap<>();
        extension.put(REQUESTS, false);
        extension.put(RESPONSES, false);
        extension.put(VALIDATION_DETAILS, true);
        return extension;
    }

    private Map<String, Object> updateExtension(Map<String, Object> extension, OpenAPISpec spec) {

        if (spec.validationDetails != ASINOPENAPI)
            extension.put(VALIDATION_DETAILS, toYesNo(spec.validationDetails));

        if (spec.validateRequests != ASINOPENAPI)
            extension.put(REQUESTS, toYesNo(spec.validateRequests));

        if (spec.validateResponses != ASINOPENAPI)
            extension.put(RESPONSES, toYesNo(spec.validateResponses));

        if (spec.validateSecurity != ASINOPENAPI)
            extension.put(SECURITY, toYesNo(spec.validateSecurity));

        if(spec.validateSecurity == YES && spec.validateRequests == NO)
            log.warn("Automatically enabled request validation; which is required by security validation.");

        extension.putIfAbsent(SECURITY, false);

        if (extension.get(SECURITY).equals(true))
            extension.put(REQUESTS, true);

        extension.putIfAbsent(REQUESTS, false);
        extension.putIfAbsent(RESPONSES, false);

        return extension;
    }

    private boolean toYesNo(OpenAPISpec.YesNoOpenAPIOption option) {
        return option == YES;
    }

    private File[] getOpenAPIFiles(String directoryName) {
        File dir = new File(directoryName);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new ConfigurationException(format("Cannot open directory %s. Please check the OpenAPI configuration of your API.", dir.getAbsolutePath()));
        }
        return dir.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json"));
    }
}
