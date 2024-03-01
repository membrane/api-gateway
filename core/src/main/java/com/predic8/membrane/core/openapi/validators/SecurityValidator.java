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
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import static com.predic8.membrane.core.security.BasicHttpSecurityScheme.BASIC;
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

    public ValidationErrors validateSecurity(ValidationContext ctx, Request request, Operation operation) {

        ctx = ctx.statusCode(403); // Forbidden

        ValidationErrors errors = checkGlobalSecurity(ctx, request);
        errors.add(checkOperationSecurity(ctx, operation, request));
        return errors;
    }

    private ValidationErrors checkGlobalSecurity(ValidationContext ctx, Request request) {
        if (api.getSecurity() == null)
            return new ValidationErrors();

        return validateDisjunctiveSecurityRequirement(ctx, api.getSecurity(), request);
    }

    private ValidationErrors checkOperationSecurity(ValidationContext ctx, Operation operation, Request request) {
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
    private ValidationErrors validateDisjunctiveSecurityRequirement(ValidationContext ctx, List<SecurityRequirement> requirements, Request request) {

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

    private ValidationErrors checkSecurityRequirements(ValidationContext ctx, SecurityRequirement securityRequirement, Request request) {
        return securityRequirement.keySet().stream() // Names of SecurityRequirements
                .map(requirementName -> checkSingleRequirement(ctx, securityRequirement, request, requirementName))
                .reduce(new ValidationErrors(), ValidationErrors::add);
    }

    private ValidationErrors checkSingleRequirement(ValidationContext ctx, SecurityRequirement securityRequirement, Request request, String schemeName) {
        log.debug("Checking mechanism: " + schemeName);

        ValidationErrors errors = new ValidationErrors();

        SecurityScheme securityScheme = api.getComponents().getSecuritySchemes().get(schemeName);

        switch (securityScheme.getType()) {
            case HTTP: {
                // See Scheme Values: https://www.iana.org/assignments/http-authschemes/http-authschemes.xhtml
                if (securityScheme.getScheme().equalsIgnoreCase("basic")) {
                    // Check if Exchange was authenticated using Basic Auth
                    if (request.getSecuritySchemes().contains(BASIC)) {
                        return errors;
                    }
                    errors.add(ctx.statusCode(401), "Caller ist not authenticated with HTTP and %s.".formatted(BASIC));
                }
                break;
            }
            case APIKEY: {
                checkApiKey(ctx, request, securityScheme, errors);
                break;
            }
            case OAUTH2: {
                checkOAuth2(ctx, request, securityScheme, errors);
                break;
            }
            // Implement: ..."apiKey", "http", "mutualTLS", "oauth2", "openIdConnect". See:
            //                APIKEY("apiKey"),
            //                        HTTP("http"),
            //                        OAUTH2("oauth2"),
            //                        OPENIDCONNECT("openIdConnect"),
            //                        MUTUALTLS("mutualTLS");
            default: {
                throw new RuntimeException("Should not happen");
            }
        }

        for (String scope : securityRequirement.get(schemeName)) {
            log.debug("Checking scope: " + scope);
            if (request.getScopes() == null || !request.getScopes().contains(scope)) {
                log.info("Caller of {} {} ist not in scope {} required by OpenAPI definition.", ctx.getMethod(), ctx.getPath(), scope);
                errors.add(ctx, "Caller ist not in scope %s".formatted(scope));
            }
        }
        return errors;
    }

    private void checkOAuth2(ValidationContext ctx, Request request, SecurityScheme securityScheme, ValidationErrors errors) {

        AtomicBoolean apiSchemeIsInRequest = new AtomicBoolean();

        List<ValidationError> e = getSecuritySchemes(request, OAuth2SecurityScheme.class).map(scheme1 -> {
            if (scheme1 instanceof OAuth2SecurityScheme oAuth2SecurityScheme) {
                apiSchemeIsInRequest.set(true);
                if (securityScheme.getFlows() != null) {

                    if (securityScheme.getFlows().getClientCredentials() != null) {
                        OAuthFlow flow =securityScheme.getFlows().getClientCredentials();
                        Scopes scopes = flow.getScopes();
                        System.out.println("scopes = " + scopes);
                        
                        List<String> missingScopes = scopes.keySet().stream()
                                .map(scope -> oAuth2SecurityScheme.hasScope(scope) ?  Optional.<String>empty(): Optional.of(scope))
                                .flatMap(Optional::stream).toList();

                        System.out.println("missingScopes = " + missingScopes);

                        if (missingScopes.isEmpty()) {
                            return Optional.of(new ValidationError(ctx, "Call is not in scopes %s.".formatted(missingScopes)));
                        }
                     }

//                    if (!securityScheme. .getIn().toString().equalsIgnoreCase(oAuth2SecurityScheme.in.toString())) {
//                        return Optional.of(new ValidationError(ctx, "Api-key is in %s but should be in %s".formatted(oAuth2SecurityScheme.in, securityScheme.getIn())));
//                    }
                }
            }
            return Optional.<ValidationError>empty();
        }).flatMap(Optional::stream).toList();

        if (!apiSchemeIsInRequest.get()) {
            errors.add(ctx.statusCode(401),"Authentication by API key is required.");
        }

        errors.add(e);
    }

    private void checkApiKey(ValidationContext ctx, Request request, SecurityScheme securityScheme, ValidationErrors errors) {

        AtomicBoolean apiSchemeIsInRequest = new AtomicBoolean();

        List<ValidationError> e = getSecuritySchemes(request,ApiKeySecurityScheme.class).map(scheme1 -> {
            if (scheme1 instanceof ApiKeySecurityScheme apiKeySecurityScheme) {
                apiSchemeIsInRequest.set(true);
                if (securityScheme.getName() != null) {
                    if (!securityScheme.getName().equalsIgnoreCase(apiKeySecurityScheme.name)) {
                        return Optional.of(new ValidationError(ctx, "Name of api-key is %s but should be %s".formatted(apiKeySecurityScheme.name, securityScheme.getName())));
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

        if (!apiSchemeIsInRequest.get()) {
            errors.add(ctx.statusCode(401),"Authentication by API key is required.");
        }

        errors.add(e);
    }

    public Stream<com.predic8.membrane.core.security.SecurityScheme> getSecuritySchemes(Request request, Class<? extends com.predic8.membrane.core.security.SecurityScheme> clazz) {
        if (request.getSecuritySchemes() == null)
            return Stream.empty();

        return request.getSecuritySchemes().stream().filter(scheme -> scheme.getClass().equals(clazz));
    }
}