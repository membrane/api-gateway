/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
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

/**
 * Thrown by {@link XmlToJsonConverter} when an object element carries a child element or attribute
 * that no schema property maps to, although the schema forbids additional properties. The converter
 * only picks up declared properties, so without this check undeclared content would be dropped
 * before validation and still reach the backend in the original body.
 */
public class UndeclaredContentException extends RuntimeException {

    public UndeclaredContentException(String kind, String name, String elementName) {
        super("The %s '%s' in element '%s' is not declared in the schema. The object does not allow additional properties."
                .formatted(kind, name, elementName));
    }
}
