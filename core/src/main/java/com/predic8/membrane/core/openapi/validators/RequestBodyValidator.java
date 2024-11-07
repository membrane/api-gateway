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
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

public class RequestBodyValidator extends AbstractBodyValidator<Request<? extends Body>> {

    @Override
    public int getDefaultStatusCode() {
        return 400;
    }

    @Override
    public String getMessageName() {
        return "Request";
    }

    public RequestBodyValidator(OpenAPI api) {
        super(api);
    }

    @Override
    protected int getStatusCodeForWrongMediaType() {
        return 415;
    }

    ValidationErrors validate(ValidationContext ctx, Request<?> request, Operation operation) {
        System.out.println("request = " + request);
        System.out.println("operation = " + operation);


        ValidationErrors errors = new ValidationErrors();
        if (operation.getRequestBody() == null) {
            if (!request.hasBody())
                return errors;
            else
                return errors.add(ctx.statusCode(400),"Request has a body although it shouldn't.");
        }

        if (operation.getRequestBody().getContent() != null) {
            errors.add(validateBodyInternal(ctx, request, operation.getRequestBody().getContent()));
        } else {
            String ref = operation.getRequestBody().get$ref();
            if (ref != null) {
                errors.add(validateBodyInternal(ctx, request, getRequestBodyFromSchema(ref).getContent()));
            } else {
                throw new RuntimeException("Should not happen!");
            }
        }

        return errors;
    }

    private RequestBody getRequestBodyFromSchema(String ref) {
        return api.getComponents().getRequestBodies().get(Utils.getComponentLocalNameFromRef(ref));
    }
}