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

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MCElement {
	String name();
	String id() default "";
	boolean mixed() default false;
	boolean topLevel() default true;
	String configPackage() default "";

    /**
     * If set, this activates the 'no envelope' mode for this element.
     *
     * In 'no envelope' mode, the class annotated by <code>@MCElement</code> must have no <code>@MCAttribute</code>
     * setter methods, no <code>@MCTextContent</code> setter methods, no <code>@MCOtherAttributes</code> setter methods
     * and exactly one <code>@MCChildElement</code> setter, which must accept a List or Collection as parameter.
     *
     * In JSON/YAML representations, the element's content is then represented directly by the item list
     *
     * ["item1":{}, "item2":{}]
     *
     * instead of
     *
     * {"propertyName": ["item1":{}, "item2":{}]}
     *
     * .
     *
     * This does not have any effect on the XML grammar.
     */
    boolean noEnvelope() default false;
}
