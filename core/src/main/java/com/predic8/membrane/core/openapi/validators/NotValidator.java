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
public class NotValidator {

    private final OpenAPI api;
    private final Schema schema;

    public NotValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object obj) {
        Schema notSchema = schema.getNot();

        ValidationErrors ve = new SchemaValidator(api,notSchema).validate(ctx,obj);
        if (ve.size() > 0)
            return null;

        return ValidationErrors.create(ctx,"Subschema is declared with not. Should not validate against subschema.");
    }


}
