/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.kubernetes.Bean.Scope.SINGLETON;


/**
 * Spring-like bean definition usable in YAML components:
 * @yaml
 * <pre><code>
 * components:
 *   &lt;id&gt;:
 *     bean:
 *       class: com.example.MyInterceptor
 *       scope: SINGLETON
 *       constructorArgs:
 *         - constructorArg:
 *             value: foo
 *       properties:
 *         - property:
 *             name: bar
 *             value: baz
 * </code></pre>
 * See <a href="https://docs.spring.io/spring-framework/reference/core/beans/definition.html">Bean Overview</a>
 */
@MCElement(name = "bean")
public class Bean {

    private String className;

    private Scope scope = SINGLETON;

    private List<ConstructorArg> constructorArgs = new ArrayList<>();

    private List<Property> properties = new ArrayList<>();

    @MCAttribute
    public void setClass(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    @MCAttribute
    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @MCAttribute
    public void setConstructorArgs(List<ConstructorArg> constructorArgs) {
        this.constructorArgs = constructorArgs;
    }

    public List<ConstructorArg> getConstructorArgs() {
        return constructorArgs;
    }

    @MCAttribute
    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public List<Property> getProperties() {
        return properties;
    }

    /**
     * see <a href="https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html">Bean Scopes</a>
     */
    public enum Scope {
        SINGLETON,
        PROTOTYPE,
        REQUEST,
        SESSION,
        APPLICATION,
        WEBSOCKET;
    }

    @MCElement(name = "constructorArg", component = false)
    public static class ConstructorArg {
        private String value;
        private String ref;

        @MCAttribute
        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @MCAttribute
        public void setRef(String ref) {
            this.ref = ref;
        }

        public String getRef() {
            return ref;
        }
    }

    @MCElement(name = "property", component = false)
    public static class Property {
        private String name;
        private String value;
        private String ref;

        @MCAttribute
        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @MCAttribute
        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @MCAttribute
        public void setRef(String ref) {
            this.ref = ref;
        }

        public String getRef() {
            return ref;
        }
    }
}
