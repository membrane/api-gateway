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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.interceptor.*;

import java.util.*;

/**
 * Configuration for a single WSDL operation exposed via OpenAPI.
 * The WSDL operation name is used as the YAML key; its value holds the settings.
 */
@MCElement(name = "operation", component = false)
public class OperationConfig {

    private String name;
    private OperationSettings settings;

    /**
     * Captures the operation name from the YAML key and its settings from the value.
     * E.g. <code>getAll: {method: GET, path: articles}</code>
     */
    @MCOtherAttributes
    public void setEntry(Map<String, OperationSettings> entry) {
        if (entry == null || entry.isEmpty()) return;
        var e = entry.entrySet().iterator().next();
        this.name = e.getKey();
        this.settings = e.getValue();
    }

    public String getName() {
        return name;
    }

    public String getMethod() {
        return settings != null ? settings.getMethod() : "POST";
    }

    public String getPath() {
        return settings != null ? settings.getPath() : null;
    }

    public List<Interceptor> getFlow() {
        return settings != null ? settings.getFlow() : List.of();
    }
}
