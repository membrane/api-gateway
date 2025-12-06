/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Annotation to exclude specific fields from being included in schemas
 * during schema generation for different configuration formats.
 *
 * This annotation can be applied to fields to specify whether they should
 * be excluded from JSON Schema (YAML) or XSD (XML configuration).
 *
 * - Setting `json` to true excludes the field from JSON Schema.
 * - Setting `xsd` to true excludes the field from XSD.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface MCExcludeFromSchema {
    boolean json() default false;  // excludes from JSON Schema (YAML)
    boolean xsd() default false;   // excludes from XSD (XML configuration)
}


