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
import io.swagger.v3.oas.models.parameters.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static java.util.Locale.ROOT;
import static java.util.Optional.*;

public abstract class AbstractParameterValidator {

    final OpenAPI api;
    final PathItem pathItem;

    protected AbstractParameterValidator(OpenAPI api, PathItem pathItem) {
        this.api = api;
        this.pathItem = pathItem;
    }

    protected Stream<Parameter> getParametersOfType(Operation operation, Class<?> paramClazz) {
        return getAllParameter(operation).stream().filter(p -> isTypeOf(p, paramClazz));
    }

    /**
     * Operation level parameters are overwriting parameters on the path level. But only
     * If in like query or header is the same.
     *
     * @param operation
     * @return
     */
    protected List<Parameter> getAllParameter(Operation operation) {
        Objects.requireNonNull(operation, "operation must not be null");

        List<Parameter> pathParams = ofNullable(pathItem.getParameters()).orElseGet(List::of);
        List<Parameter> opParams = ofNullable(operation.getParameters()).orElseGet(List::of);

        // Sample key set: [number|query, string|query, bool|query, other|header]
        Map<String, Parameter> byKey = new LinkedHashMap<>();


        // path-level first, then operation-level to override
        Stream.concat(pathParams.stream(), opParams.stream())
                .filter(Objects::nonNull)
                .forEach(p -> byKey.put(getParameterKey(p), p));
        return new ArrayList<>(byKey.values());
    }

    private static @NotNull String getParameterKey(Parameter p) {
        return p.getName() + "|" + getInNormalized(p);
    }

    private static @NotNull String getInNormalized(Parameter p) {
        return p.getIn() == null ? "" : p.getIn().toLowerCase(ROOT);
    }

    boolean isTypeOf(Parameter p, Class<?> clazz) {
        return clazz.isInstance(p);
    }

    protected Schema<?> getSchema(Parameter p) {
        Schema<?> schema = p.getSchema();
        if (schema == null) {
            return null;
        }
        if (schema.get$ref() != null) {
            String componentLocalNameFromRef = getComponentLocalNameFromRef(schema.get$ref());
            return api.getComponents().getSchemas().get(componentLocalNameFromRef);
        }
        return schema;
    }
}
