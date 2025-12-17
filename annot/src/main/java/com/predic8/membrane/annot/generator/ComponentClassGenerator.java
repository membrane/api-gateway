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
