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

package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.Request;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class SecurityValidator {

    private final Logger log = getLogger(SecurityValidator.class);

    OpenAPI api;

    public SecurityValidator(OpenAPI api) {
        this.api = api;
    }

    public ValidationErrors validateSecurity(ValidationContext ctx, Request request, Operation operation) {
        ValidationErrors errors = new ValidationErrors();
        checkGlobalSecurity(ctx, errors, request);
        checkOperationSecurity(ctx,errors,operation, request);
        return errors;
    }

    private void checkOperationSecurity(ValidationContext ctx, ValidationErrors errors, Operation operation, Request request) {
        operation.getSecurity().forEach(requirement -> checkSecurityRequirements(ctx, requirement, errors, request));
    }

    private void checkGlobalSecurity(ValidationContext ctx, ValidationErrors errors, Request request) {
        api.getSecurity().forEach(requirement -> checkSecurityRequirements(ctx, requirement, errors, request));
    }

    private void checkSecurityRequirements(ValidationContext ctx, SecurityRequirement securityRequirement, ValidationErrors errors, Request request) {
        log.info("securityRequirement = " + securityRequirement);
        log.info("securityRequirement = " + securityRequirement.keySet());

        for (String key : securityRequirement.keySet()) {
            log.info("key = " + key);
            for (String scope : securityRequirement.get(key)) {
                log.info("v = " + scope);

             if(request.getScopes()==null || !request.getScopes().contains(scope)) {
                 errors.add(ctx, "Caller ist not in scope %s".formatted(scope));
             }
            }
        }
    }
}
