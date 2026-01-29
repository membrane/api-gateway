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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a setter that receives "additional / unforeseen" configuration attributes that are not explicitly
 * modeled via {@link MCAttribute}.
 *
 * <p>The annotated method must accept a {@link java.util.Map}:
 * <ul>
 *   <li>{@code Map<String, String>} for arbitrary string attributes, or</li>
 *   <li>{@code Map<String, Object>} where the values must be components.</li>
 * </ul>
 *
 * <p>Use this when an element should allow extensible, free-form attributes without extending the grammar
 * for every possible key.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MCOtherAttributes {
}
