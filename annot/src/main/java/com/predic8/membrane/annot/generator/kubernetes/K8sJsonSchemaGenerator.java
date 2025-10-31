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

import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.annot.generator.kubernetes.model.*;
import com.predic8.membrane.annot.model.*;

import javax.annotation.processing.*;
import javax.tools.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.*;
import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.schema;

/**
 * Generates JSON Schema (draft 2019-09/2020-12) to validate Kubernetes CustomResourceDefinitions.
 */
public class K8sJsonSchemaGenerator extends AbstractK8sGenerator {

    public K8sJsonSchemaGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    public void write(Model m) throws IOException {
        try {
            for (MainInfo main : m.getMains()) {
                assemble(m, main);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void assemble(Model m, MainInfo main) throws IOException {
        for (ElementInfo elementInfo : getTopLevelElementInfos(main)) {
            String name = elementInfo.getAnnotation().name().toLowerCase();
            FileObject fo = createFileObject(main, name + ".schema.json");
            try (BufferedWriter w = new BufferedWriter(fo.openWriter())) {
                assembleBase(m, w, main, elementInfo);
            }
        }
    }

    private void assembleBase(Model m, Writer w, MainInfo main, ElementInfo i) throws IOException {
        if (i.getAnnotation().mixed() && !i.getChildElementSpecs().isEmpty()) {
            throw new ProcessingException(
                    "@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
                    i.getElement()
            );
        }

        String eleName = i.getAnnotation().name();

        Schema schema = schema(eleName);
        collectDefinitions(m, main, i, schema);
        collectProperties(m, main, i, schema);

        w.append( writer.writeValueAsString(schema.json(JsonNodeFactory.instance.objectNode())));
    }

    private void collectAttributes(ElementInfo i, SchemaObject so) {
        i.getAis().stream()
                .filter(ai -> !ai.getXMLName().equals("id"))
                .forEach(ai -> {
                    SchemaObject sop = object(ai.getXMLName());
                    sop.type(ai.getSchemaType(processingEnv.getTypeUtils()));
//                    sop.addAttribute("type", ai.getSchemaType(processingEnv.getTypeUtils()));
                    sop.required(ai.isRequired());
                    so.property(sop);
                });
    }

    private void collectProperties(Model m, MainInfo main, ElementInfo i, SchemaObject schema) {
        collectAttributes(i, schema);
        collectTextContent(i, schema);
        collectChildElements(m, main, i, schema);
    }

    private void collectDefinitions(Model m, MainInfo main, ElementInfo i, Schema schema) {
        Map<String, ElementInfo> all = new LinkedHashMap<>();

        Stack<ElementInfo> stack = new Stack<>();
        stack.push(i);

        while (!stack.isEmpty()) {
            ElementInfo current = stack.pop();
            current.getChildElementSpecs().stream()
                    .flatMap(cei -> main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo().stream())
                    .filter(ei -> !all.containsKey(ei.getXSDTypeName(m)))
                    .forEach(ei -> {
                        all.put(ei.getXSDTypeName(m), ei);
                        stack.push(ei);
                    });
        }

        if (all.isEmpty())
            return;

        for (Map.Entry<String, ElementInfo> entry : all.entrySet()) {
            SchemaObject so = object(entry.getKey())
                    .type(entry.getValue().getAnnotation().noEnvelope() ? "array" : "object")
                            .additionalProperties(entry.getValue().getOai() != null);

            collectProperties(m, main, entry.getValue(), so);

            schema.definition(so);
        }
    }

    private void collectTextContent(ElementInfo i, SchemaObject so) {
        if (i.getTci() == null)
            return;
        so.property(string(i.getTci().getPropertyName()));
    }

    private void collectChildElements(Model m, MainInfo main, ElementInfo i, AbstractSchema so) {
        for (ChildElementInfo cei : i.getChildElementSpecs()) {
            boolean isList = cei.isList();

            AbstractSchema parent2 = so;

            if (isList) {
                SchemaObject items =  object("items").additionalProperties( cei.getAnnotation().allowForeign());

                if (i.getAnnotation().noEnvelope() && so instanceof SchemaArray sa) {
                    sa.items(items);
                } else {
                    if (so instanceof SchemaObject sObj) {
                        //sop.addAttribute("additionalItems", false);
                        sObj.property(array(cei.getPropertyName()).required(cei.isRequired()));
                    }

                }
                parent2 = items;
            } else {
                if (cei.getAnnotation().allowForeign()) {
                   // parent2.setAdditionalProperties(true);
                }
            }

            for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
                SchemaRef sop = ref(ei.getAnnotation().name());
                //sop.setRequired(cei.isRequired());
                // TODO only one is required, not all
                sop.ref("#/$defs/" + ei.getXSDTypeName(m));
                ((SchemaObject)parent2).property(sop);
            }
        }
    }

    @Override
    protected String fileName() {
        return "";
    }
}