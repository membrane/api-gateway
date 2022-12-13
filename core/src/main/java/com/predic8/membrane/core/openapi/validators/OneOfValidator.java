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
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;

@SuppressWarnings("rawtypes")
public class OneOfValidator {

    private final OpenAPI api;
    private final Schema schema;

    public OneOfValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object obj) {
        @SuppressWarnings("unchecked")
        List<Schema> oneOfSchemas = schema.getOneOf();

        AtomicInteger numberValid = new AtomicInteger();
        oneOfSchemas.forEach(schema -> {
             if (!areThereErrors(new SchemaValidator(api,schema).validate(ctx,obj))) {
                 numberValid.incrementAndGet();
             }
        });

        if (numberValid.get() == 1) {
            return null;
        }
        return ValidationErrors.create(ctx,String.format("OneOf requires that exactly one subschema is valid. But there are %d subschemas valid.",numberValid.get()));
    }


}
