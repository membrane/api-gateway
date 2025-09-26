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
import com.predic8.membrane.annot.generator.kubernetes.model.RefObj;
import com.predic8.membrane.annot.generator.kubernetes.model.Schema;
import com.predic8.membrane.annot.generator.kubernetes.model.SchemaObject;
import com.predic8.membrane.annot.model.*;
import com.predic8.membrane.annot.model.doc.Doc;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        List<RefObj> oneOfArray = new ArrayList<>();

        for (ElementInfo elementInfo : main.getElements().values()) {

            if (elementInfo.getAnnotation().mixed() && !elementInfo.getChildElementSpecs().isEmpty()) {
                throw new ProcessingException(
                        "@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
                        elementInfo.getElement()
                );
            }

            SchemaObject parser = new SchemaObject(elementInfo.getXSDTypeName(m));
            parser.addAttribute("type", elementInfo.getAnnotation().noEnvelope() ? "array" : "object");
            parser.addAttribute("additionalProperties", elementInfo.getOai() != null);
            parser.addAttribute("description", getDescriptionAsText(elementInfo));
            parser.addAttribute("x-intellij-html-description", getDescriptionAsHtml(elementInfo));
            collectProperties(m, main, elementInfo, parser);

            if (elementInfo.getAnnotation().topLevel()) {

                SchemaObject envelope = new SchemaObject(elementInfo.getXSDTypeName(m).replaceFirst("Parser$", "Envelope"));

                envelope.addAttribute("additionalProperties", false);
                envelope.addAttribute("required", List.of("\"spec\""));
                envelope.addProperty(
                        new SchemaObject("apiVersion") {{
                            addAttribute("type", "string");
                        }});
                envelope.addProperty(new SchemaObject("kind") {{
                    addAttribute("type", "string");
                    addAttribute("enum", List.of("\"" + elementInfo.getAnnotation().name() + "\""));
                }});
                envelope.addProperty(new SchemaObject("metadata") {{
                    addAttribute("type", "object");
                }});
                envelope.addProperty(new SchemaObject("spec") {{
                    addAttribute("$ref", "#/definitions/" + parser.getName());
                }});

                schema.addDefinition(envelope);

                oneOfArray.add(new RefObj("#/definitions/" + envelope.getName()));
            }

            schema.addDefinition(parser);

        }

        schema.addAttribute("oneOf", oneOfArray);

        FileObject fo = createFile(main);
        try (BufferedWriter w = new BufferedWriter(fo.openWriter())) {
            w.write(schema.toString());
        }
    }

    private String getDescriptionContent(AbstractJavadocedInfo elementInfo) {
        Doc doc = elementInfo.getDoc(processingEnv);
        if (doc == null) {
            return "";
        }
        return doc.getEntries().stream()
                .filter(e -> "description".equals(e.getKey()))
                .map(e -> e.getValueAsXMLSnippet(false))
                .findFirst().orElse("");
    }

    private String getDescriptionAsText(AbstractJavadocedInfo elementInfo) {
        return escapeJsonContent(getDescriptionContent(elementInfo).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim());
    }

    private String getDescriptionAsHtml(AbstractJavadocedInfo elementInfo) {
        return escapeJsonContent(getDescriptionContent(elementInfo).replaceAll("\\s+", " ").trim());
    }

    private static String escapeJsonContent(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x",(int)c));
                    else          sb.append(c);
            }
        }
        return sb.toString();
    }


    private FileObject createFile(MainInfo main) throws IOException {
        List<Element> sources = new ArrayList<>(main.getInterceptorElements());
        sources.add(main.getElement());

        return processingEnv.getFiler()
                .createResource(
                        StandardLocation.CLASS_OUTPUT,
                        "com.predic8.membrane.core.config.json",
                        "membrane.schema.json",
                        sources.toArray(new Element[0])
                );
    }


    private void collectAttributes(ElementInfo i, ISchema so) {
        i.getAis().stream()
                .filter(ai -> !ai.getXMLName().equals("id"))
                .forEach(ai -> {
                    SchemaObject sop = new SchemaObject(ai.getXMLName());
                    sop.addAttribute("description", getDescriptionAsText(ai));
                    sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(ai));
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
        sop.addAttribute("description", getDescriptionAsText(i));
        sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(i));
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

                if (i.getAnnotation().noEnvelope()) {
                    so.addAttribute("items", items);
                } else {
                    SchemaObject sop = new SchemaObject(cei.getPropertyName());
                    sop.setRequired(cei.isRequired());
                    sop.addAttribute("description", getDescriptionAsText(cei));
                    sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(cei));
                    sop.addAttribute("type", "array");
                    sop.addAttribute("additionalItems", false);
                    sop.addAttribute("items", items);

                    so.addProperty(sop);
                }

                parent2 = items;
            } else {
                if (cei.getAnnotation().allowForeign())
                    parent2.setAdditionalProperties(true);
            }

            for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
                SchemaObject sop = new SchemaObject(ei.getAnnotation().name());
                sop.addAttribute("description", getDescriptionAsText(ei));
                sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(ei));
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
