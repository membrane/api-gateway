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
 * Configuration for a single WSDL operation exposed via OpenAPI
 */
@MCElement(name = "operation")
public class OperationConfig {

    private String name;
    private List<Interceptor> transformation = new ArrayList<>();

    public String getName() {
        return name;
    }

    /**
     * @description The WSDL operation name (e.g. getOrders, createOrder)
     * @example getOrders
     */
    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public List<Interceptor> getTransformation() {
        return transformation;
    }

    /**
     * @description Interceptors applied to this operation (e.g. apiKey, rateLimiter)
     */
    @MCChildElement
    public void setTransformation(List<Interceptor> transformation) {
        this.transformation = transformation;
    }
}
