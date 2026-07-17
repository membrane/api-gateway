/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.slf4j.*;

import java.util.*;

/**
 * Generates OpenAPI 3.0 specification from WSDL definitions
 */
public class OpenApiGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenApiGenerator.class);
    
    private final Definitions definitions;
    private final String basePath;
    private final Map<String, OperationConfig> operations;

    public OpenApiGenerator(Definitions definitions, String basePath, Map<String, OperationConfig> operations) {
        this.definitions = definitions;
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        this.operations = operations;
    }

    public String generateYaml() {
        StringBuilder yaml = new StringBuilder();
        
        // OpenAPI header
        yaml.append("openapi: 3.0.0\n");
        yaml.append("info:\n");
        yaml.append("  title: ").append(escapeYaml(getServiceName())).append("\n");
        yaml.append("  description: Auto-generated OpenAPI from WSDL\n");
        yaml.append("  version: 1.0.0\n");
        yaml.append("\n");
        
        // Servers
        yaml.append("servers:\n");
        yaml.append("  - url: ").append(basePath).append("\n");
        yaml.append("\n");
        
        // Paths
        yaml.append("paths:\n");
        
        for (Map.Entry<String, OperationConfig> entry : operations.entrySet()) {
            String operationName = entry.getKey();
            String path = "/" + camelToKebab(operationName);
            
            yaml.append("  ").append(path).append(":\n");
            yaml.append("    post:\n");
            yaml.append("      operationId: ").append(operationName).append("\n");
            yaml.append("      summary: ").append(operationName).append("\n");
            yaml.append("      requestBody:\n");
            yaml.append("        required: true\n");
            yaml.append("        content:\n");
            yaml.append("          application/json:\n");
            yaml.append("            schema:\n");
            yaml.append("              type: object\n");
            yaml.append("              description: Request parameters for ").append(operationName).append("\n");
            yaml.append("      responses:\n");
            yaml.append("        '200':\n");
            yaml.append("          description: Successful response\n");
            yaml.append("          content:\n");
            yaml.append("            application/json:\n");
            yaml.append("              schema:\n");
            yaml.append("                type: object\n");
            yaml.append("        '500':\n");
            yaml.append("          description: Internal server error\n");
        }
        
        return yaml.toString();
    }

    private String getServiceName() {
        List<Service> services = definitions.getServices();
        if (!services.isEmpty()) {
            return services.get(0).getName();
        }
        return "SOAP Service";
    }

    private String camelToKebab(String camelCase) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('-');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String escapeYaml(String value) {
        if (value == null) {
            return "";
        }
        // Basic YAML escaping
        if (value.contains(":") || value.contains("#") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }
}
