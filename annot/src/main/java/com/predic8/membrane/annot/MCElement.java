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

    /**
     * Optional identifier for the element. Must be used when defining a local element (component=false) which uses the same `name` as a component (component=true).
     */
    String id() default "";

    /**
     * Only relevant for XML configuration on classes that use {@link MCTextContent}.
     *
     * <p>By default, the text content is treated as plain character data, e.g.:
     * <pre>{@code
     * <demoTextContent>Put your text here</demoTextContent>
     * }</pre>
     *
     * <p>If the text contains markup like:
     * <pre>{@code
     * <demoTextContent><h1>Example html text</h1></demoTextContent>
     * }</pre>
     * the parser may interpret the inner tags (e.g., {@code <h1>}) as child elements.
     *
     * <p>Setting {@code mixed = true} allows element content that mixes text and nested XML,
     * preventing such tags from being treated as configuration child elements.
     */
    boolean mixed() default false;

    /**
     * Whether the element can be defined at the top-level of the config.
     */
    boolean topLevel() default false;

    /**
     * Whether the element can be a separate bean in the XML schema, or a separate document in YAML/JSON.
     */
    boolean component() default true;

    String configPackage() default "";

    /**
     * If set, this activates the 'no envelope' mode for this element.
     * <p>
     * In 'no envelope' mode, the class annotated by <code>@MCElement</code> must have no <code>@MCAttribute</code>
     * setter methods, no <code>@MCTextContent</code> setter methods, no <code>@MCOtherAttributes</code> setter methods
     * and exactly one <code>@MCChildElement</code> setter, which must accept a List or Collection as parameter.
     * <p>
     * In JSON/YAML representations, the element's content is then represented directly by the item list
     * <p>
     * ["item1":{}, "item2":{}]
     * <p>
     * instead of
     * <p>
     * {"propertyName": ["item1":{}, "item2":{}]}
     * <p>
     * .
     * <p>
     * This does not have any effect on the XML grammar.
     */
    boolean noEnvelope() default false;

    /**
     * Whether the element should be configurable as part of the interceptor flow
     */
    boolean excludeFromFlow() default false;

    /**
     * Whether the element has only one attribute.
     * Enables inline yaml object configuration
     * e.g.
     * <pre><code>
     * allow: foo
     * </code></pre>
     * instead of
     * <pre><code>
     * allow:
     *   value: foo
     *  </code></pre>
     */
    boolean collapsed() default false;
}
