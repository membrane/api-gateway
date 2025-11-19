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

import com.fasterxml.jackson.annotation.*;
import com.predic8.membrane.annot.*;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.ASINOPENAPI;

/**
 * @description Reads an OpenAPI description and deploys an API with the information of it.
 */
@MCElement(name = "openapi", topLevel = false)
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
     * @description Turn validation of requests on or off.
     * @example yes
     * @default no
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
     * @description Turn validation of responses on or off.
     * @example yes
     * @default no
     */
    @MCAttribute
    public void setValidateResponses(YesNoOpenAPIOption validateResponses) {
        this.validateResponses = validateResponses;
    }

    /**
     * @description Show details of the validation to the caller.
     * @example yes
     * @default no
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