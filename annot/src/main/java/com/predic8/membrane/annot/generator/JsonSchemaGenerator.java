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
package com.predic8.membrane.annot.generator;

import com.predic8.membrane.annot.ProcessingException;
import com.predic8.membrane.annot.generator.kubernetes.AbstractK8sGenerator;
import com.predic8.membrane.annot.generator.kubernetes.model.ISchema;
import com.predic8.membrane.annot.generator.kubernetes.model.Schema;
import com.predic8.membrane.annot.generator.kubernetes.model.SchemaObject;
import com.predic8.membrane.annot.model.ChildElementInfo;
import com.predic8.membrane.annot.model.ElementInfo;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

public class JsonSchemaGenerator extends AbstractK8sGenerator {

    public JsonSchemaGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    public void write(Model m) {
        try {
            for (MainInfo main : m.getMains()) {
                assemble(m, main);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void assemble(Model m, MainInfo main) throws IOException {
        Schema schema = new Schema("membrane");
        schema.setAdditionalProperties(false);
        schema.addProperty(new SchemaObject("apiVersion") {{
            addAttribute("type", "string");
        }});
        schema.addProperty(new SchemaObject("metadata") {{
            addAttribute("type", "object");
        }});
        schema.addProperty(new SchemaObject("kind") {{
            addAttribute("type", "string");
        }});

        Collection<ElementInfo> elementInfos = main.getElements().values();

        for (ElementInfo elementInfo : elementInfos) {

            if (elementInfo.getAnnotation().mixed() && !elementInfo.getChildElementSpecs().isEmpty()) {
                throw new ProcessingException(
                        "@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
                        elementInfo.getElement()
                );
            }

            SchemaObject subSchema = new SchemaObject(elementInfo.getXSDTypeName(m));
            subSchema.addAttribute("type", "object");
            subSchema.addAttribute("additionalProperties", elementInfo.getOai() != null);

            collectProperties(m, main, elementInfo, subSchema);
            schema.addDefinition(subSchema);
        }
        FileObject fo = createFile(main, "membrane.schema.json");
        try (BufferedWriter w = new BufferedWriter(fo.openWriter())) {
            w.write(schema.toString());
        }
    }


    private FileObject createFile(MainInfo main, String fileName) throws IOException {
        List<Element> sources = new ArrayList<>(main.getInterceptorElements());
        sources.add(main.getElement());

        return processingEnv.getFiler()
                .createResource(
                        StandardLocation.CLASS_OUTPUT,
                        "com.predic8.membrane.core.config.json",
                        fileName,
                        sources.toArray(new Element[0])
                );
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

    private void collectProperties(Model m, MainInfo main, ElementInfo i, SchemaObject schema) {
        collectAttributes(i, schema);
        collectTextContent(i, schema);
        collectChildElements(m, main, i, schema);
    }

    private void collectTextContent(ElementInfo i, ISchema so) {
        if (i.getTci() == null)
            return;

        SchemaObject sop = new SchemaObject(i.getTci().getPropertyName());
        sop.addAttribute("type", "string");
        so.addProperty(sop);
    }

    private void collectChildElements(Model m, MainInfo main, ElementInfo i, ISchema so) {
        for (ChildElementInfo cei : i.getChildElementSpecs()) {
            boolean isList = cei.isList();

            ISchema parent2 = so;

            if (isList) {
                SchemaObject items = new SchemaObject("items");
                items.addAttribute("type", "object");
                items.addAttribute("additionalProperties", cei.getAnnotation().allowForeign());

                SchemaObject sop = new SchemaObject(cei.getPropertyName());
                sop.setRequired(cei.isRequired());
                sop.addAttribute("type", "array");
                sop.addAttribute("additionalItems", false);
                sop.addAttribute("items", items);

                so.addProperty(sop);

                parent2 = items;
            } else {
                if (cei.getAnnotation().allowForeign())
                    parent2.setAdditionalProperties(true);
            }

            for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
                SchemaObject sop = new SchemaObject(ei.getAnnotation().name());
                sop.setRequired(cei.isRequired());
                sop.addAttribute("$ref", "#/definitions/" + ei.getXSDTypeName(m));
                parent2.addProperty(sop);
            }
        }
    }

    @Override
    protected String fileName() {
        return "";
    }
}
