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

package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @description
 * <p>Maps JSON-RPC method names to their schema validation definitions.</p>
 *
 * <p>In YAML, the entries are written as a map under <code>schemaValidation.methods</code>.</p>
 */
@MCElement(name = "methods", component = false, id = "json-rpc-method-definitions")
public class JsonRPCMethodsDefinitions {

    private final Map<String, JsonRPCMethodSchemas> methods = new LinkedHashMap<>();

    /**
     * @description
     * <p>Defines the per-method schema validation entries.</p>
     *
     * <p>Each key must match one JSON-RPC <code>method</code> value exactly.</p>
     *
     * @example "rpc.echo": { params: { location: "classpath:/json/rpc/echo-params.schema.json" } }
     */
    @MCOtherAttributes
    public void setMethods(Map<String, JsonRPCMethodSchemas> methods) {
        if (methods != null) {
            this.methods.putAll(methods);
        }
    }

    public Map<String, JsonRPCMethodSchemas> getMethods() {
        return methods;
    }
}
