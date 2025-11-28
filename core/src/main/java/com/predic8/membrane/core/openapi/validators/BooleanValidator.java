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

import com.fasterxml.jackson.databind.node.TextNode;
import tools.jackson.databind.node.BooleanNode;

import static java.util.Locale.ROOT;

public class BooleanValidator implements JsonSchemaValidator {

    @Override
    public String canValidate(Object obj) {
        String str = getStringValue(obj).toLowerCase(ROOT);
        if (obj instanceof BooleanNode || str.equals("true") || str.equals("false") || str.equals("yes") || str.equals("no"))
            return BOOLEAN;
        return null;
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object value) {
        if (value instanceof BooleanNode)
            return null;

        String str = getStringValue(value).toLowerCase(ROOT);
        if (str.equals("true") || str.equals("false") || str.equals("yes") || str.equals("no"))
            return null;

        return ValidationErrors.error(ctx.schemaType("boolean"), String.format("Value '%s' is not a boolean (true/false).", value));
    }

    private static String getStringValue(Object value) {
        if (value instanceof TextNode tn) {
            return tn.asText().trim();
        }
        if (value instanceof String s) {
            return s.trim();
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        return "";
    }
}
