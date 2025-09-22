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

public interface JsonSchemaValidator {

    String NULL = "null";
    String NUMBER = "number";
    String ARRAY = "array";
    String OBJECT = "object";
    String STRING = "string";
    String BOOLEAN = "boolean";
    String INTEGER = "integer";

    /**
     * Determines if the given object can be validated and returns its type as a string.
     *
     * <p>Each implementing class specifies the type it can validate. If the object is of a valid type,
     * this method returns a string representing that type (e.g., "string" for a string object).
     * If the object cannot be validated or is null, the method returns null.
     *
     * @param value the object to be checked for validation
     * @return the type name as a string if the object is valid, or null if it cannot be validated
     */
    String canValidate(Object value);

    /**
     * Validates value against a schema definition. Implementations can just return null if there is no error to avoid object creation.
     *
     * @return null or empty ValidationErrors
     */
    ValidationErrors validate(ValidationContext ctx, Object value);
}
