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

import com.predic8.membrane.core.openapi.validators.ValidationContext.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.util.CollectionsUtil.*;

public abstract class AbstractParameterValidator {

    final OpenAPI api;
    final PathItem pathItem;

    protected AbstractParameterValidator(OpenAPI api, PathItem pathItem) {
        this.api = api;
        this.pathItem = pathItem;
    }

    protected Stream<Parameter> getParametersOfType(Operation operation, Class<?> paramClazz) {
        return getAllParameterSchemas(operation).stream().filter(p -> isTypeOf(p, paramClazz));
    }

    protected List<Parameter> getAllParameterSchemas(Operation operation) {
        return concat(pathItem.getParameters(), operation.getParameters());
    }

    boolean isTypeOf(Parameter p, Class<?> clazz) {
        return p.getClass().equals(clazz);
    }

    private static ValidationContext getCtx(ValidationContext ctx, Parameter param, ValidatedEntityType type) {
        return ctx.entity(param.getName())
                .entityType(type)
                .statusCode(400);
    }

    protected Schema getSchema(Parameter p) {
        Schema schema = p.getSchema();
        if (schema == null) {
            return null;
        }
        if(schema.get$ref() != null) {
            String componentLocalNameFromRef = getComponentLocalNameFromRef(schema.get$ref());
            return  api.getComponents().getSchemas().get(componentLocalNameFromRef);
        }
        return schema;
    }
}
