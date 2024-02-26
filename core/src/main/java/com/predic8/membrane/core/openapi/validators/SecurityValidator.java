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

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.security.*;

public class SecurityValidator {

    OpenAPI api;

    public SecurityValidator(OpenAPI api) {
        this.api = api;
    }

    public ValidationErrors validateSecurity(ValidationContext ctx, Operation operation) {
        ValidationErrors errors = new ValidationErrors();
        checkGlobalSecurity(ctx, errors);
        checkOperationSecurity(ctx,errors,operation);
        return errors;
    }

    private void checkOperationSecurity(ValidationContext ctx, ValidationErrors errors, Operation operation) {
        operation.getSecurity().forEach(requirement -> checkSecurityRequirements(ctx, requirement, errors));
    }

    private void checkGlobalSecurity(ValidationContext ctx, ValidationErrors errors) {
        api.getSecurity().forEach(requirement -> checkSecurityRequirements(ctx, requirement, errors));
    }

    private static void checkSecurityRequirements(ValidationContext ctx, SecurityRequirement securityRequirement, ValidationErrors errors) {
        System.out.println("securityRequirement = " + securityRequirement);
        System.out.println("securityRequirement = " + securityRequirement.keySet());

        for (String key : securityRequirement.keySet()) {
            System.out.println("key = " + key);
            for (String value : securityRequirement.get(key)) {
                System.out.println("v = " + value);
                errors.add(ctx, "Caller ist not in scope %s".formatted(value));
            }
        }
    }
}
