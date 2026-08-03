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
    private String path;
    private String method = "POST";
    private List<Interceptor> flow = new ArrayList<>();

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

    public String getPath() {
        return path;
    }

    /**
     * @description URL path segment for this operation.
     * @example budget-structures
     */
    @MCAttribute
    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    /**
     * @description HTTP method exposed for this operation. Defaults to POST.
     * @example GET
     */
    @MCAttribute
    public void setMethod(String method) {
        this.method = method;
    }

    public List<Interceptor> getFlow() {
        return flow;
    }

    /**
     * @description Interceptors applied to this operation.
     * On the request side they run before JSON-to-SOAP conversion and before the SOAP backend call,
     * so plugins like <code>apiKey</code> or <code>rateLimiter</code> can reject early.
     * On the response side they run after SOAP-to-JSON conversion on the final JSON body.
     */
    @MCChildElement
    public void setFlow(List<Interceptor> flow) {
        this.flow = flow;
    }
}
