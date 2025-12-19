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

public class ComponentClassGenerator extends ClassGenerator{


    public ComponentClassGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    @Override
    protected String getClassName() {
        return "Components";
    }

    @Override
    protected String getClassImpl() {
        return """
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
                          if (this.components == null)
                            this.components = new java.util.LinkedHashMap<>();
                          if (components != null)
                            this.components.putAll(components);
                    }
                }
                """;
    }
}
