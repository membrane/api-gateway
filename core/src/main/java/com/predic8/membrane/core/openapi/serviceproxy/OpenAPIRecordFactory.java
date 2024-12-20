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
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import io.swagger.parser.*;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.parser.*;
import io.swagger.v3.parser.core.models.*;
import org.apache.commons.lang3.exception.*;
import org.jetbrains.annotations.*;
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
        File[] files = getOpenAPIFiles(spec.dir);
        if (files == null) {
            log.warn("Directory %s does not contain any OpenAPI documents.".formatted(spec.dir));
            return;
        }
        for (File file : files) {
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
            if (root instanceof OpenAPIParsingException pe) {
                throw new ConfigurationException(format("""
                        Could not read or parse OpenAPI Document from location: %s
                        
                        Reason: %s
                        
                        Have a look at your proxies.xml configuration.
                        """, pe.getLocation(), pe.getMessage()));
            }
            if (root instanceof FileNotFoundException fnf) {
                log.error("Cannot read OpenAPI specification from location " + spec.location);
                log.error("Exception: " + fnf.getMessage());
                throw new ConfigurationException("Cannot read OpenAPI specification from location: " + spec.location);
            }

            log.error(e.getMessage(), e);

            throw new RuntimeException(e);
        }
    }

    /**
     * Gets an unique id for an API
     * @param apiRecords Map with OpenAPIRecords to test for collisions
     * @param rec Record with an parsed OpenAPI
     * @return Guaranteed unique id within the provided apiRecords
     */
    String getUniqueId(Map<String, OpenAPIRecord> apiRecords, OpenAPIRecord rec) {
        String id = getIdFromAPI(rec.api);
        if (apiRecords.get(id) != null) {
            log.warn("There are multiple OpenAPI documents with the id {}. The id is computed from the title {} and version {}. Please make sure that the documents are different or use the x-membrane-id field.",
                    id, rec.api.getInfo().getTitle(), rec.api.getInfo().getVersion());
           // Add -0 until unique
            while (apiRecords.get(id) != null)
                id += "-0";
            log.warn("Changing the id to {} in order to make them unique.", id);
        }
        return id;
    }

    private OpenAPIRecord create(OpenAPISpec spec) throws IOException {
        OpenAPI api = getOpenAPI(spec);
        OpenAPIRecord record = new OpenAPIRecord(api, getSpec(getOpenAPI(spec)), spec);
        setExtensionOnAPI(spec, record.api);
        return record;
    }

    private OpenAPIRecord create(OpenAPISpec spec, File file) throws IOException {
        OpenAPI api = parseFileAsOpenAPI(file);
        OpenAPIRecord record = new OpenAPIRecord(api, getSpec(parseFileAsOpenAPI(file)), spec);
        setExtensionOnAPI(spec, record.api);
        return record;
    }

    private OpenAPI getOpenAPI(OpenAPISpec spec) {
        String path = resolve(spec.location);
        try {
            JsonNode node = omYaml.readTree(getInputStreamForLocation(spec.location));
            OpenAPI openAPI = new OpenAPIParser().readLocation(path, null, getParseOptions()).getOpenAPI();

            addConversionNoticeIfSwagger2(openAPI, node);
            return openAPI;
        } catch (IOException e) {
            throw new OpenAPIParsingException("Could not read OpenAPI file: " + e.getMessage(), path);
        }
    }

    private InputStream getInputStreamForLocation(String location) throws ResourceRetrievalException {
        return router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), location));
    }

    private OpenAPI parseFileAsOpenAPI(File oaFile) {
        try {
            JsonNode node = omYaml.readTree(oaFile);
            OpenAPI api = new OpenAPIParser().readContents(
                    readInputStream(new FileInputStream(oaFile)),
                    null,
                    getParseOptions()
            ).getOpenAPI();

            addConversionNoticeIfSwagger2(api, node);
            return api;
        } catch (IOException e) {
            throw new OpenAPIParsingException("Could not read OpenAPI file: " + e.getMessage(), oaFile.getPath());
        }
    }

    private void addConversionNoticeIfSwagger2(OpenAPI api, JsonNode node) {
        if (isSwagger2(node) && api.getInfo() != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(api.getInfo().getDescription());
            if (api.getInfo().getDescription() != null) builder.append("\n\n");
            builder.append("***Note:*** *OpenAPI description was converted to OAS 3 from Swagger 2 by Membrane API Gateway.*");
            api.getInfo().setDescription(builder.toString());
        }
    }

    boolean isSwagger2(JsonNode node) {
        JsonNode swaggerNode = node.get("swagger");
        return swaggerNode != null && swaggerNode.asText().startsWith("2.");
    }
    
    private String resolve(String filepath) {
        return ResolverMap.combine(router.getBaseLocation(), filepath);
    }

    private static @NotNull ParseOptions getParseOptions() {
        ParseOptions parseOptions = new ParseOptions();

        // Resolve $refs in remote or relative locations, parse referenced document and remove $refs
        // See: https://github.com/swagger-api/swagger-parser?tab=readme-ov-file#1-resolve
        parseOptions.setResolve(true);

        return parseOptions;
    }

    private JsonNode getSpec(OpenAPI api) throws IOException {
        return omYaml.readTree(Json31.mapper().writeValueAsBytes(api));
    }

    private void setExtensionOnAPI(OpenAPISpec spec, OpenAPI api) {
        if (api.getExtensions() == null) {
            api.setExtensions(new HashMap<>());
        }
        api.getExtensions().put(X_MEMBRANE_VALIDATION, updateExtension(getXValidationExtension(api), spec));
    }

    private static Map<String, Object> getXValidationExtension(OpenAPI api) {
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
