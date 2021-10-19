/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.generator.kubernetes;

import com.predic8.membrane.annot.ProcessingException;
import com.predic8.membrane.annot.generator.kubernetes.model.ISchema;
import com.predic8.membrane.annot.generator.kubernetes.model.Schema;
import com.predic8.membrane.annot.generator.kubernetes.model.SchemaObject;
import com.predic8.membrane.annot.model.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Generates Json Schema draft 4 to validate kubernetetes CustomResourceDefinitions.
 */
public class JsonSchemaGenerator extends AbstractK8sGenerator {

    public JsonSchemaGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    public void write(Model m) {
        try {
            for (MainInfo main : m.getMains()) {
                assemble(main);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void assemble(MainInfo main) throws IOException {
        for (ElementInfo elementInfo : getRules(main)) {
            String name = elementInfo.getAnnotation().name().toLowerCase();
            FileObject fo = createFileObject(main, name + ".schema.json");
            try (BufferedWriter w = new BufferedWriter(fo.openWriter())) {
                assembleBase(w, main, elementInfo);
            }
        }
    }

    private void assembleBase(Writer w, MainInfo main, ElementInfo i) throws IOException {
        if (i.getAnnotation().mixed() && i.getCeis().size() > 0) {
            throw new ProcessingException(
                    "@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
                    i.getElement()
            );
        }

        String eleName = i.getAnnotation().name();

        Schema schema = new Schema(eleName);
        collectDefinitions(main, i, schema);
        collectProperties(main, i, schema);

        w.append(schema.toString());
    }

    private void collectAttributes(ElementInfo i, ISchema so) {
        i.getAis().stream()
                .filter(ai -> !ai.getXMLName().equals("id"))
                .forEach(ai -> {
                    SchemaObject sop = new SchemaObject(ai.getXMLName());
                    sop.addAttribute("type", ai.getSchemaType(processingEnv.getTypeUtils()));
                    sop.setRequired(ai.isRequired());
                    so.addProperty(sop);
                });
    }

    private void collectProperties(MainInfo main, ElementInfo i, Schema schema) {
        collectAttributes(i, schema);

        for (ChildElementInfo cei : i.getCeis()) {
            boolean isInterceptor = cei.isList() && cei.getTypeDeclaration().getSimpleName().toString().equals("Interceptor");

            for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
                SchemaObject sop = new SchemaObject(ei.getAnnotation().name());
                sop.setRequired(cei.isRequired());
                sop.addAttribute("$ref", "#/definitions/" + ei.getAnnotation().name());
                if (isInterceptor)
                    schema.addInterceptor(sop);
                else
                    schema.addProperty(sop);
            }
        }
    }

    private void collectDefinitions(MainInfo main, ElementInfo i, Schema schema) {
        Map<String, ElementInfo> all = new HashMap<>();

        Stack<ElementInfo> stack = new Stack<>();
        stack.push(i);

        while (!stack.isEmpty()) {
            ElementInfo current = stack.pop();
            current.getCeis().stream()
                    .flatMap(cei -> main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo().stream())
                    .filter(ei -> !all.containsKey(ei.getAnnotation().name()))
                    .forEach(ei -> {
                        all.put(ei.getAnnotation().name(), ei);
                        stack.push(ei);
                    });
        }

        if (all.isEmpty())
            return;

        for (Map.Entry<String, ElementInfo> entry : all.entrySet()) {
            SchemaObject so = new SchemaObject(entry.getKey());
            so.addAttribute("type", "object");
            so.addAttribute("additionalProperties", false);

            collectAttributes(entry.getValue(), so);
            collectTextContent(entry.getValue(), so);
            collectChildElements(main, entry.getValue(), so);

            schema.addDefinition(so);
        }
    }

    private void collectTextContent(ElementInfo i, SchemaObject so) {
        if (i.getTci() == null)
            return;

        SchemaObject sop = new SchemaObject(i.getTci().getPropertyName());
        sop.addAttribute("type", "string");
        so.addProperty(sop);
    }

    private void collectChildElements(MainInfo main, ElementInfo i, SchemaObject so) {
        for (ChildElementInfo cei : i.getCeis()) {
            for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
                SchemaObject sop = new SchemaObject(ei.getAnnotation().name());
                sop.setRequired(cei.isRequired());
                sop.addAttribute("$ref", "#/definitions/" + ei.getAnnotation().name());
                so.addProperty(sop);
            }
        }
    }

    @Override
    protected String fileName() {
        return "";
    }
}
