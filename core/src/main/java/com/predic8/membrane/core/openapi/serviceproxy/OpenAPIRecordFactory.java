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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.openapi.OpenAPIParsingException;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.util.ConfigurationException;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.*;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.getIdFromAPI;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.isSwagger2;
import static com.predic8.membrane.core.util.FileUtil.readInputStream;
import static com.predic8.membrane.core.util.URIUtil.convertPath2FilePathString;
import static java.lang.String.format;

public class OpenAPIRecordFactory {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIRecordFactory.class.getName());

    private static final ObjectMapper omYaml = YAMLMapper.builder().build();

    private final Router router;

    public OpenAPIRecordFactory(Router router) {
        this.router = router;
    }

    public Map<String, OpenAPIRecord> create(Collection<OpenAPISpec> specs) {
        Map<String, OpenAPIRecord> apiRecords = new LinkedHashMap<>();
        for (OpenAPISpec spec : specs) {
            // Maybe a spec has both location and dir.
            addOpenApisFromLocation(apiRecords, spec);
            addOpenApisFromDirectory(apiRecords, spec);
        }
        return apiRecords;
    }

    private void addOpenApisFromDirectory(Map<String, OpenAPIRecord> apiRecords, OpenAPISpec spec) {
        if (spec.dir == null)
            return;

        log.info("Parsing specs from dir {}", spec.dir);
        File[] files = getOpenAPIFiles(spec.dir);
        if (files == null) {
            log.warn("Directory {} does not contain any OpenAPI documents.", spec.dir);
            return;
        }
        for (File file : files) {
            log.info("Parsing spec {}", file);
            OpenAPISpec fileSpec = spec.clone();
            fileSpec.setLocation(spec.dir + "/" + file.getName());
            OpenAPIRecord rec = create(fileSpec, file);
            apiRecords.put(getUniqueId(apiRecords, rec), rec);
        }
    }

    private void addOpenApisFromLocation(Map<String, OpenAPIRecord> apiRecords, OpenAPISpec spec) {
        if (spec.location == null)
            return;

        try {
            log.debug("Parsing spec {}", spec.location);
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
                        
                        Have a look at your proxies.xml configuration.
                        """, pe.getLocation()), pe);
            }
            if (root instanceof FileNotFoundException fnf) {
                throw new ConfigurationException("Cannot read OpenAPI specification from location: " + spec.location, fnf);
            }
            String msg = "While parsing spec %s .".formatted(spec.location);
            log.error(msg, e);
            throw new ConfigurationException(msg, e);
        }
    }

    /**
     * Gets an unique id for an API
     *
     * @param apiRecords Map with OpenAPIRecords to test for collisions
     * @param rec        Record with an parsed OpenAPI
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
        OpenAPIRecord record = new OpenAPIRecord(getOpenAPI(spec), spec);
        setExtensionOnAPI(spec, record.api);
        return record;
    }

    private OpenAPIRecord create(OpenAPISpec spec, File file) {
        OpenAPIRecord record = new OpenAPIRecord(parseFileAsOpenAPI(file), spec);
        setExtensionOnAPI(spec, record.api);
        return record;
    }

    private OpenAPI getOpenAPI(OpenAPISpec spec) throws IOException {
        OpenAPI openAPI = parseFromLocation(spec);
        addConversionNoticeIfSwagger2(openAPI, omYaml.readTree(getInputStreamForLocation(spec.location)));
        return openAPI;

    }

    private OpenAPI parseFromLocation(OpenAPISpec spec) {
        return new OpenAPIParser().readLocation(convertPathToFileUriPathIfNeeded(resolve(spec.location)), null, getParseOptions()).getOpenAPI();
    }

    private static @NotNull String convertPathToFileUriPathIfNeeded(String path) {
        // Convert normal path to file URI path if needed
        if (!path.startsWith("http:") && !path.startsWith("https:") && !path.startsWith("file:")) {
            path = convertPath2FilePathString(path);
        }
        return path;
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
        if (!isSwagger2(node) || api.getInfo() == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(api.getInfo().getDescription());
        if (api.getInfo().getDescription() != null) builder.append("\n\n");
        builder.append("***Note:*** *This OpenAPI description was converted from Swagger 2 to OAS 3 by Membrane API Gateway!*");
        api.getInfo().setDescription(builder.toString());
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

        if (spec.validateSecurity == YES && spec.validateRequests == NO)
            log.warn("Automatically enabled request validation; which is required by security validation.");

        extension.putIfAbsent(SECURITY, false);

        if (extension.get(SECURITY).equals(true))
            extension.put(REQUESTS, true);

        extension.putIfAbsent(REQUESTS, false);
        extension.putIfAbsent(RESPONSES, false);

        return extension;
    }

    private boolean toYesNo(OpenAPISpec.YesNoOpenAPIOption option) {
        return option == YES || option == TRUE;
    }

    private File[] getOpenAPIFiles(String directoryName) {
        File dir = new File(directoryName);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new ConfigurationException(format("Cannot open directory %s. Please check the OpenAPI configuration of your API.", dir.getAbsolutePath()));
        }
        return dir.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json"));
    }
}
