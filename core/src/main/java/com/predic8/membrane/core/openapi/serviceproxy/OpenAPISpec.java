/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.ASINOPENAPI;

/**
 * @description Reads an OpenAPI description, deploys an API from it and validates the messages
 * flowing through against that description. What runs is governed by the
 * <code>validateRequests</code>, <code>validateResponses</code> and <code>validateSecurity</code>
 * attributes; when enabled, Membrane validates:
 * <ul>
 *   <li>Routing: the request path and HTTP method against the declared operations, including the
 *       OpenAPI 3.2 <code>QUERY</code> method and <code>additionalOperations</code> (an unknown path
 *       returns 404, an undeclared method 405).</li>
 *   <li>Path, query and header parameters (type, <code>required</code>, <code>enum</code> and
 *       <code>format</code>), including the OpenAPI 3.2 <code>in: querystring</code> parameter.</li>
 *   <li>Request and response bodies against their JSON Schema (JSON Schema 2020-12 for OpenAPI 3.1
 *       and 3.2) for <code>application/json</code>, XML, <code>application/x-www-form-urlencoded</code>
 *       and <code>multipart/form-data</code>.</li>
 *   <li>Streaming and sequential bodies item by item through the OpenAPI 3.2 <code>itemSchema</code>,
 *       for <code>application/jsonl</code>, <code>application/json-seq</code>, ND-JSON and
 *       <code>text/event-stream</code>.</li>
 *   <li>XML bodies, including the OpenAPI 3.2 <code>xml.nodeType</code>
 *       (<code>attribute</code>, <code>element</code>, <code>text</code>, <code>cdata</code>)
 *       alongside the deprecated <code>xml.attribute</code> and <code>xml.wrapped</code>.</li>
 *   <li>String content that carries another format, via <code>contentMediaType</code>,
 *       <code>contentEncoding</code> and <code>contentSchema</code>.</li>
 *   <li>Response headers.</li>
 *   <li>Declared security requirements (for example API keys and OAuth2 scopes) when
 *       <code>validateSecurity</code> is enabled.</li>
 * </ul>
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   openapi:
 *     - location: openapi/fruitshop-api.yaml
 *       validateRequests: yes
 *       validateResponses: yes
 * </code></pre>
 */
@MCElement(name = "openapi", component = false)
public class OpenAPISpec implements Cloneable {

    public String location;
    public String dir;
    public YesNoOpenAPIOption validateRequests = ASINOPENAPI;
    public YesNoOpenAPIOption validateResponses = ASINOPENAPI;
    public YesNoOpenAPIOption validationDetails = ASINOPENAPI;
    public YesNoOpenAPIOption validateSecurity = ASINOPENAPI;
    private Rewrite rewrite = new Rewrite();

    @Override
    public OpenAPISpec clone() {
        try {
            OpenAPISpec clone = (OpenAPISpec) super.clone();
            clone.rewrite = this.rewrite.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Should never happen", e);
        }
    }

    public OpenAPISpec() {
    }

    public boolean hasRewrite() {
        return rewrite != null;
    }

    @MCChildElement(order = 50)
    public void setRewrite(Rewrite rewrite) {
        this.rewrite = rewrite;
    }

    public Rewrite getRewrite() {
        return rewrite;
    }

    public String getLocation() {
        return location;
    }

    /**
     * @description Filename or URL pointing to an OpenAPI document. Relative filenames use the %MEMBRANE_HOME%/conf folder as base directory.
     * @example openapi/fruitstore-v1.yaml, <a href="https://api.predic8.de/shop/swagger">https://api.predic8.de/shop/swagger</a>
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String getDir() {
        return dir;
    }

    /**
     * @description Directory containing OpenAPI definitions to deploy.
     * @example openapi
     */
    @MCAttribute
    public void setDir(String dir) {
        this.dir = dir;
    }

    public YesNoOpenAPIOption getValidateRequests() {
        return validateRequests;
    }

    /**
     * @description Whether requests are validated against the OpenAPI description. When omitted, the
     * setting is taken from the OpenAPI document's <code>x-membrane-validation</code> extension,
     * which defaults to off. Enabling security validation also enables request validation.
     * @example yes
     * @default asInOpenAPI
     */
    @MCAttribute
    public void setValidateRequests(YesNoOpenAPIOption validateRequests) {
        this.validateRequests = validateRequests;
    }

    @SuppressWarnings("unused")
    public YesNoOpenAPIOption getValidateResponses() {
        return validateResponses;
    }

    /**
     * @description Whether responses are validated against the OpenAPI description. When omitted, the
     * setting is taken from the OpenAPI document's <code>x-membrane-validation</code> extension,
     * which defaults to off.
     * @example yes
     * @default asInOpenAPI
     */
    @MCAttribute
    public void setValidateResponses(YesNoOpenAPIOption validateResponses) {
        this.validateResponses = validateResponses;
    }

    /**
     * @description Whether validation error responses include the detailed list of validation errors.
     * When omitted, the setting is taken from the OpenAPI document's <code>x-membrane-validation</code>
     * extension, which defaults to on.
     * @example no
     * @default asInOpenAPI
     */
    @MCAttribute
    public void setValidationDetails(YesNoOpenAPIOption validationDetails) {
        this.validationDetails = validationDetails;
    }

    public YesNoOpenAPIOption getValidationDetails() {
        return validationDetails;
    }

    @SuppressWarnings("unused")
    public YesNoOpenAPIOption getValidateSecurity() {
        return validateSecurity;
    }

    /**
     * @description Whether requests are checked against the operation's security requirements (for
     * example API keys and OAuth2 scopes). Enabling it also enables request validation. When omitted,
     * the setting is taken from the OpenAPI document's <code>x-membrane-validation</code> extension,
     * which defaults to off.
     * @example yes
     * @default asInOpenAPI
     */
    @MCAttribute
    public void setValidateSecurity(YesNoOpenAPIOption validateSecurity) {
        this.validateSecurity = validateSecurity;
    }

    public enum YesNoOpenAPIOption {
        YES,
        NO,
        ASINOPENAPI,

        // To allow reading from YAML with yes and no without quotes
        TRUE,
        FALSE
    }

    @Override
    public String toString() {
        return "OpenAPISpec{" +
               "location='" + location + '\'' +
               ", dir='" + dir + '\'' +
               ", validateRequests=" + validateRequests +
               ", validateResponses=" + validateResponses +
               ", validationDetails=" + validationDetails +
               ", validateSecurity=" + validateSecurity +
               ", rewrite=" + rewrite +
               '}';
    }
}