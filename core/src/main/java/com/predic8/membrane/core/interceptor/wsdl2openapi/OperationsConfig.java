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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @description Configures which WSDL operations are exposed via OpenAPI. Add one attribute per
 * operation you want to expose, naming it after the WSDL operation and giving it an
 * <code>OperationSettings</code> value. Omitting this element exposes every WSDL operation with
 * default settings.
 * See tutorials/wsdl-to-openapi/20-WSDL-to-OpenAPI-REST.yaml.
 * @yaml <pre><code>
 * operations:
 *   getPartner:
 *     method: GET
 *     path: /partners/{id}
 *     tag: Partner
 *   createPartner:
 *     method: POST
 *     path: /partners
 *     tag: Partner
 * </code></pre>
 */
@MCElement(name = "operations", component = false)
public class OperationsConfig {

    private final Map<String, OperationSettings> map = new LinkedHashMap<>();

    /**
     * @description Each attribute name is a WSDL operation name and its value configures that operation.
     */
    @MCOtherAttributes
    public void setEntry(Map<String, OperationSettings> entry) {
        if (entry != null) map.putAll(entry);
    }

    public Map<String, OperationSettings> getMap() {
        return Collections.unmodifiableMap(map);
    }
}
