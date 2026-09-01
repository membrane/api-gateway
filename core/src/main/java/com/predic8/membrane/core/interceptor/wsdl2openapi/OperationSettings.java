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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Per-operation settings for a WSDL operation exposed via OpenAPI.
 */
@MCElement(name = "operationSettings", component = false)
public class OperationSettings {

    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");

    private String method = "POST";
    private String path;
    private String tag;
    private List<Interceptor> flow = new ArrayList<>();

    public String getMethod() {
        return method;
    }

    /**
     * @description HTTP method exposed for this operation. Defaults to POST.
     * GET and DELETE have no request body: the input message fields named in the <code>path</code>
     * template become path parameters, and the remaining fields become query parameters. A field
     * that carries more than a single value, such as a nested structure or a repeating element,
     * cannot be passed this way and is rejected at startup — expose such an operation as POST.
     * An XSD attribute is addressed by its plain name, without the <code>@</code> the JSON request
     * body uses for it.
     * @example GET
     */
    @MCAttribute
    public void setMethod(String method) {
        if (method == null) throw new ConfigurationException("HTTP method must not be null");
        String upper = method.toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(upper)) throw new ConfigurationException(
                "Unsupported HTTP method: " + method + ". Allowed: " + ALLOWED_METHODS);
        this.method = upper;
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
        this.path = path != null && path.startsWith("/") ? path.substring(1) : path;
    }

    public String getTag() {
        return tag;
    }

    /**
     * @description OpenAPI tag assigned to this operation. Groups the operation under the given tag in API documentation.
     * @example Partner
     */
    @MCAttribute
    public void setTag(String tag) {
        this.tag = tag;
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
