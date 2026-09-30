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
package com.predic8.membrane.core.interceptor.discovery;

import java.util.*;

/**
 * A single operation of an API, taken from the OpenAPI document describing it. Only the
 * <code>membrane</code> discovery format renders these; APIs.json and RFC 9727 link to the
 * OpenAPI document instead.
 *
 * @param method      uppercase HTTP method, e.g. <code>GET</code>
 * @param path        path template as written in the OpenAPI document, e.g. <code>/products/{id}</code>
 * @param operationId <code>operationId</code> of the operation, or null
 * @param summary     one line summary of the operation, or null
 * @param tags        tags of the operation, never null
 * @param deprecated  whether the operation is marked deprecated
 */
public record CatalogOperation(String method, String path, String operationId, String summary, List<String> tags, boolean deprecated) {

    public CatalogOperation {
        tags = List.copyOf(tags);
    }

}
