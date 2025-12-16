package com.predic8.membrane.annot.generator;

import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BeanClassGenerator {
    private final ProcessingEnvironment processingEnv;

    public BeanClassGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    /**
     * @return true if the file was written, false if it already existed
     */
    public boolean writeJava(Model m) throws IOException {
        for (MainInfo main : m.getMains()) {
            List<Element> sources = new ArrayList<>();
            sources.addAll(main.getInterceptorElements());
            sources.add(main.getElement());
            try {
                FileObject o = processingEnv.getFiler().createSourceFile(
                        "com.predic8.membrane.annot.bean.Bean"
                );

                try (BufferedWriter bw = new BufferedWriter(o.openWriter())) {
                    String copyright = """
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
                            """;

                    bw.write(copyright +
                            "\r\n" +
                            "package " + main.getAnnotation().outputPackage() + ";\r\n" +
                            """
                                    import com.predic8.membrane.annot.MCAttribute;
                                    import com.predic8.membrane.annot.MCChildElement;
                                    import com.predic8.membrane.annot.MCElement;
                                    
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
                                            PROTOTYPE,
                                            REQUEST,
                                            SESSION,
                                            APPLICATION,
                                            WEBSOCKET
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
                                    """);
                }
                return true;
            } catch (FilerException e) {
                if (e.getMessage().contains("Source file already created"))
                    return false;
                if (e.getMessage().contains("Attempt to recreate a file for"))
                    return false;
                throw e;
            }
        }

        return false;
    }
}
