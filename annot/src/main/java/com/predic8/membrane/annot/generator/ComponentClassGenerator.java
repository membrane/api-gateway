package com.predic8.membrane.annot.generator;

import com.predic8.membrane.annot.model.*;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ComponentClassGenerator {
    private final ProcessingEnvironment processingEnv;

    public ComponentClassGenerator(ProcessingEnvironment processingEnv) {
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
                        main.getAnnotation().outputPackage() + ".Components",
                        new Element[0]);
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
                                    import com.predic8.membrane.annot.MCElement;
                                    import com.predic8.membrane.annot.MCOtherAttributes;
                                    
                                    import java.util.Map;
                                    
                                    @MCElement(name = "components", topLevel = true)
                                    public class Components {
                                    
                                        Map<String, Object> components;
                                    
                                        public Map<String, Object> getComponents() {
                                            return components;
                                        }
                                    
                                        @MCOtherAttributes
                                        public void setComponents(Map<String, Object> components) {
                                            this.components.putAll(components);
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
