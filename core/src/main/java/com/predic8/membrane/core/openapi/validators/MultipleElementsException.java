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
 * Thrown by {@link XmlToJsonConverter} when an element occurs more than once although the schema
 * expects a single value. Instead of silently keeping only the first occurrence (and dropping the
 * rest), the conversion is rejected so a validation error can be reported.
 */
public class MultipleElementsException extends RuntimeException {

    public MultipleElementsException(String elementName) {
        super("Element '%s' occurs more than once, but the schema allows only a single value here."
                .formatted(elementName));
    }
}
