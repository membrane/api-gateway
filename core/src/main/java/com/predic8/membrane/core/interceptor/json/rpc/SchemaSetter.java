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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;

public class SchemaSetter {

    protected String location;
    protected JsonRPCInlineSchema schema;

    /**
     * @description
     * <p>References the JSON Schema by path or URL.</p>
     *
     * <p>Configure either <code>location</code> or <code>schema</code>, but not both.</p>
     *
     * @example classpath:/json/rpc/echo-params.schema.json
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    /**
     * @description
     * <p>Defines the JSON Schema inline.</p>
     *
     * <p>Configure either <code>schema</code> or <code>location</code>, but not both.</p>
     */
    @MCChildElement(order = 1)
    public void setSchema(JsonRPCInlineSchema schema) {
        this.schema = schema;
    }

    public JsonRPCInlineSchema getSchema() {
        return schema;
    }
}
