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

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.security.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import static com.predic8.membrane.core.security.BasicHttpSecurityScheme.*;
import static org.slf4j.LoggerFactory.*;

/**
 * See:
 * - <a href="https://spec.openapis.org/oas/v3.1.0#security-scheme-object">Security Scheme Object</a>
 * -
 */
public class SecurityValidator {

    private final Logger log = getLogger(SecurityValidator.class);

    private final OpenAPI api;

    public SecurityValidator(OpenAPI api) {
        this.api = api;
    }

    public ValidationErrors validateSecurity(ValidationContext ctx, Request<?> request, Operation operation) {

        ctx = ctx.statusCode(403); // Forbidden

        ValidationErrors errors = checkGlobalSecurity(ctx, request);
        errors.add(checkOperationSecurity(ctx, operation, request));
        return errors;
    }

    private ValidationErrors checkGlobalSecurity(ValidationContext ctx, Request<?> request) {
        if (api.getSecurity() == null)
            return new ValidationErrors();

        return validateDisjunctiveSecurityRequirement(ctx, api.getSecurity(), request);
    }

    private ValidationErrors checkOperationSecurity(ValidationContext ctx, Operation operation, Request<?> request) {
        if (operation.getSecurity() == null)
            return new ValidationErrors();

        return validateDisjunctiveSecurityRequirement(ctx, operation.getSecurity(), request);
    }

    /**
     * Given an OpenAPI:
     * security:
     * - a1: []
     * a2: []
     * a3: []
     * - b1:  []
     * b2: []
     * - c: []
     * To be valid either:
     * - a1,a2,a3 or
     * - b1, b2 or
     * - c
     * must be valid.
     */
    private ValidationErrors validateDisjunctiveSecurityRequirement(ValidationContext ctx, List<SecurityRequirement> requirements, Request<?> request) {

        ValidationErrors errors = new ValidationErrors();

        // No streaming because we exit the method within the loop
        for (SecurityRequirement requirement : requirements) {
            ValidationErrors errorsInt = checkSecurityRequirements(ctx, requirement, request);

            if (errorsInt.isEmpty()) {
                // One of the alternatives is true, that's enough, let's get out
                return new ValidationErrors();
            }

            errors.add(errorsInt);
        }

        return errors;
    }

    private ValidationErrors checkSecurityRequirements(ValidationContext ctx, SecurityRequirement requirement, Request<?> request) {
        return requirement.keySet().stream() // Names of SecurityRequirements
                .map(requirementName -> checkSingleRequirement(ctx, requirement, request, requirementName))
                .reduce(new ValidationErrors(), ValidationErrors::add);
    }

    private ValidationErrors checkSingleRequirement(ValidationContext ctx, SecurityRequirement requirement, Request<?> request, String schemeName) {
        log.debug("Checking mechanism: " + schemeName);

        ValidationErrors errors = new ValidationErrors();

        Map<String, SecurityScheme> securitySchemes = api.getComponents().getSecuritySchemes();

        if (securitySchemes == null) {
            log.error("In OpenAPI with title '%s' there are no securitySchemes. Check the OpenAPI document!".formatted(getOpenAPITitle()));
            return getValidationErrorsProblemServerSide(ctx);
        }

        SecurityScheme schemeDefinition = securitySchemes.get(schemeName);

        if (schemeDefinition == null) {
            log.error("In OpenAPI with title '%s' there is no securityScheme '%s'. Check the OpenAPI document!".formatted(getOpenAPITitle(),schemeName));
            return getValidationErrorsProblemServerSide(ctx);
        }

        // Type field on securityScheme not set
        if (schemeDefinition.getType() == null) {
            log.error("In OpenAPI with title '%s' the securityScheme '%s' has no type. Check the OpenAPI document!".formatted(api.getInfo().getTitle(),schemeName));
            return getValidationErrorsProblemServerSide(ctx);
        }

        ValidationErrors errorsInt = checkSecuritySchemeType(ctx, request, schemeDefinition, errors);
        assert errorsInt != null;
        if (!errorsInt.isEmpty()) return errorsInt;

        errors.add(checkScopes(ctx, requirement, request, schemeName));
        return errors;
    }

    @NotNull
    private static ValidationErrors getValidationErrorsProblemServerSide(ValidationContext ctx) {
        return ValidationErrors.error(ctx, "There is a problem with the OpenAPI configuration at the server side.");
    }

    private String getOpenAPITitle() {
        return api.getInfo().getTitle();
    }

    /**
     * See <a href="https://spec.openapis.org/oas/v3.1.0#security-scheme-object">SecuritySchemes in OpenAPI Spec</a>
     */
    @Nullable
    private ValidationErrors checkSecuritySchemeType(ValidationContext ctx, Request<?> request, SecurityScheme scheme, ValidationErrors errors) {
        return switch (scheme.getType()) {
            case HTTP -> checkHttp(ctx, request, scheme);
            case APIKEY -> errors.add(checkApiKey(ctx, request, scheme));
            /*
             * In case of OAUTH2 or OPENIDCONNECT check for JWT or OAuth2 scheme.
             * Cause of the nature of the OAuth2 flows we cannot check what flow it is.
             */
            case OAUTH2, OPENIDCONNECT -> checkOAuth2OrOpenIdConnectScheme(ctx, request);
            case MUTUALTLS -> throw new RuntimeException("Security scheme mutualTLS is not implemented yet.");
        };
    }

    private ValidationErrors checkScopes(ValidationContext ctx, SecurityRequirement requirement, Request<?> request, String schemeName) {
        ValidationErrors errors = new ValidationErrors();
        for (String scope : requirement.get(schemeName)) {
            log.debug("Checking scope: " + scope);

            var hasScope = new AtomicBoolean();
            request.getSecuritySchemes().forEach(scheme -> {
                if (scheme.hasScope(scope)) {
                    hasScope.set(true);
                }
            });

            if (!hasScope.get()) {
                log.info("Caller of {} {} is not in scope {} required by OpenAPI definition.", ctx.getMethod(), ctx.getPath(), scope);
                errors.add(ctx, "Caller is not in scope %s".formatted(scope));
            }
        }
        return errors;
    }

    private static ValidationErrors checkHttp(ValidationContext ctx, Request<?> request, SecurityScheme schemeDefinition) {

        ValidationErrors errors = new ValidationErrors();

        switch (schemeDefinition.getScheme().toLowerCase()) {
            case "basic": {
                if (request.hasScheme(BASIC())) {
                    return errors;
                }
                errors.add(ctx.statusCode(401), "Caller is not authenticated with HTTP and %s.".formatted(BASIC()));
                return errors;
            }
            case "bearer": {
                if (request.hasScheme(BEARER())) {
                    return errors;
                }
                errors.add(ctx.statusCode(401), "Caller ist not authenticated with HTTP and %s.".formatted(BEARER()));
                return errors;
            }
        }

        errors.add(ctx.statusCode(401), "Scheme %s is not supported".formatted(schemeDefinition.getScheme()));
        return errors;
    }

    private ValidationErrors checkOAuth2OrOpenIdConnectScheme(ValidationContext ctx, Request<?> request) {
        if (securitySchemeIsNotPresent(request,OAuth2SecurityScheme.class) && securitySchemeIsNotPresent(request,JWTSecurityScheme.class)) {
            return ValidationErrors.error(ctx.statusCode(401), "OAuth2 or JWT authentication is required.");
        }
        return ValidationErrors.empty();
    }

    private ValidationErrors checkApiKey(ValidationContext ctx, Request<?> request, SecurityScheme securityScheme) {

        ValidationErrors errors = new ValidationErrors();

        AtomicBoolean schemeIsInRequest = new AtomicBoolean();

        List<ValidationError> e = getSecuritySchemes(request, ApiKeySecurityScheme.class).map(scheme1 -> {
            if (scheme1 instanceof ApiKeySecurityScheme apiKeySecurityScheme) {
                schemeIsInRequest.set(true);
                if (securityScheme.getName() != null) {
                    if (!securityScheme.getName().equalsIgnoreCase(apiKeySecurityScheme.parameterName)) {
                        return Optional.of(new ValidationError(ctx, "Name of api-key is %s but should be %s".formatted(apiKeySecurityScheme.parameterName, securityScheme.getName())));
                    }
                }
                if (securityScheme.getIn() != null) {
                    if (!securityScheme.getIn().toString().equalsIgnoreCase(apiKeySecurityScheme.in.toString())) {
                        return Optional.of(new ValidationError(ctx, "Api-key is in %s but should be in %s".formatted(apiKeySecurityScheme.in, securityScheme.getIn())));
                    }
                }
            }
            return Optional.<ValidationError>empty();
        }).flatMap(Optional::stream).toList();

        if (!schemeIsInRequest.get()) {
            errors.add(ctx.statusCode(401), "Authentication by API key is required.");
        }

        errors.add(e);
        return errors;
    }

    private boolean securitySchemeIsNotPresent(Request<?> request, Class<? extends com.predic8.membrane.core.security.SecurityScheme> clazz) {
        return getSecuritySchemes(request, clazz).findFirst().isEmpty();
    }

    private Stream<com.predic8.membrane.core.security.SecurityScheme> getSecuritySchemes(Request<?> request, Class<? extends com.predic8.membrane.core.security.SecurityScheme> clazz) {
        if (request.getSecuritySchemes() == null)
            return Stream.empty();

        return request.getSecuritySchemes().stream().filter(scheme -> scheme.getClass().equals(clazz));
    }
}