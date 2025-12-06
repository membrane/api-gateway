/* Copyright 2013 predic8 GmbH, www.predic8.com

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
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for marking a method to represent an attribute in the Membrane configuration.
 * Primarily used for defining properties and their representation across different configuration grammars,
 * such as JSON Schema or XML Schema (XSD).
 *
 * Methods annotated with this are typically setters with specific behavior or constraints
 * controlled by the annotation's attributes.
 *
 * Attributes:
 * - attributeName: Specifies the name of the attribute. If not defined, a default naming convention might apply.
 * - excludeFromJson: Indicates whether the attribute should be excluded from JSON Schema representation (default is false).
 * - excludeFromXsd: Indicates whether the attribute should be excluded from XML Schema (XSD) representation (default is false).
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface MCAttribute {
	String attributeName() default "";
    boolean excludeFromJson() default false;  // excludes from JSON Schema (YAML)
    boolean excludeFromXsd() default false;   // excludes from XSD (XML configuration)
}
