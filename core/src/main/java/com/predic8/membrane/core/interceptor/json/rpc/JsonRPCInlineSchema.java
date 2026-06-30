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
 * <p>Embeds a JSON Schema inline in the Membrane configuration.</p>
 *
 * <p>The entries inside <code>schema</code> are copied verbatim into the generated
 * JSON Schema document.</p>
 */
@MCElement(name = "schema", component = false, id = "json-rpc-inline-schema")
public class JsonRPCInlineSchema {

    private final Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * @description
     * <p>Defines the raw JSON Schema keywords for the inline schema.</p>
     *
     * @example type: object
     */
    @MCOtherAttributes
    public void setProperties(Map<String, Object> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
