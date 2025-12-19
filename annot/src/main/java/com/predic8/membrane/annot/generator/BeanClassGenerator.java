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

package com.predic8.membrane.annot.generator;

import javax.annotation.processing.ProcessingEnvironment;

public class BeanClassGenerator extends ClassGenerator{

    public BeanClassGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    @Override
    protected String getClassName() {
        return "Bean";
    }

    @Override
    protected String getClassImpl() {
        return """
                import com.predic8.membrane.annot.MCAttribute;
                import com.predic8.membrane.annot.MCChildElement;
                import com.predic8.membrane.annot.MCElement;
                import com.predic8.membrane.annot.generator.Scope;
                
                import java.util.ArrayList;
                import java.util.List;
                
                /**
                 * Spring-like bean definition usable in YAML components:
                 * components:
                 *   <id>:
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
                 */
                @MCElement(name = "bean")
                public class Bean {
                
                    private String className;
                    private Scope scope = Scope.SINGLETON;
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
                
                    @MCChildElement(order = 3)
                    public void setConstructorArgs(List<ConstructorArg> constructorArgs) {
                        this.constructorArgs = constructorArgs;
                    }
                
                    public List<ConstructorArg> getConstructorArgs() {
                        return constructorArgs;
                    }
                
                    @MCChildElement
                    public void setProperties(List<Property> properties) {
                        this.properties = properties;
                    }
                
                    public List<Property> getProperties() {
                        return properties;
                    }
                
                    public enum Scope {
                        SINGLETON,
                        PROTOTYPE
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
                """;
    }
}
