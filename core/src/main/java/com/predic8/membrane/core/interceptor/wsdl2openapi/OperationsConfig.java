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

import java.util.*;

/**
 * Named map of WSDL operations to expose via OpenAPI.
 * Each key is a WSDL operation name; the value holds its HTTP mapping settings.
 */
@MCElement(name = "operations", component = false)
public class OperationsConfig {

    private final Map<String, OperationSettings> map = new LinkedHashMap<>();

    @MCOtherAttributes
    public void setEntry(Map<String, OperationSettings> entry) {
        if (entry != null) map.putAll(entry);
    }

    public Map<String, OperationSettings> getMap() {
        return map;
    }
}
