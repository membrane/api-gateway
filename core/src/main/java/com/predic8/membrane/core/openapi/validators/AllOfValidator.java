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
import io.swagger.v3.oas.models.media.*;

import java.util.*;

@SuppressWarnings("rawtypes")
public class AllOfValidator {

    private final OpenAPI api;
    private final Schema schema;

    public AllOfValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object obj) {
        ValidationErrors errors = new ValidationErrors();

        @SuppressWarnings("unchecked")
        List<Schema> allOfSchemas = schema.getAllOf();

        allOfSchemas.forEach(schema -> errors.add(new SchemaValidator(api,schema).validate(ctx,obj)));
        if (errors.size() > 0) {
            errors.add(ctx, "One of the subschemas of allOf is not valid.");
        }
        return errors;
    }
}
