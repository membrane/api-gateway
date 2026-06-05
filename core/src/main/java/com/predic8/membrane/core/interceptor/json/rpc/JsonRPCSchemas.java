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

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

/**
 * @description
 * <p>Defines the JSON Schema validation rules for one exact JSON-RPC method name.</p>
 *
 * <p>Use <code>params</code> to validate the request payload and <code>response</code>
 * to validate successful upstream responses.</p>
 */
@MCElement(name = "method", component = false, id = "json-rpc-method-schema")
public class JsonRPCSchemas {

    private JsonRPCParamValidation paramValidation;

    private JsonRPCResponseValidation responseValidation;

    /**
     * @description Validates the JSON-RPC <code>params</code> member for this method.
     */
    @MCChildElement(order = 1)
    public void setParams(JsonRPCParamValidation paramValidation) {
        this.paramValidation = paramValidation;
    }

    public JsonRPCParamValidation getParams() {
        return paramValidation;
    }

    /**
     * @description Validates the successful JSON-RPC <code>result</code> payload for this method.
     */
    @MCChildElement(order = 2)
    public void setResponse(JsonRPCResponseValidation responseValidation) {
        this.responseValidation = responseValidation;
    }

    public JsonRPCResponseValidation getResponse() {
        return responseValidation;
    }
}
