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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.annot.generator.kubernetes.*;
import com.predic8.membrane.annot.generator.kubernetes.model.*;
import com.predic8.membrane.annot.model.*;
import com.predic8.membrane.annot.model.doc.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.*;
import java.io.*;
import java.util.*;

import static javax.tools.StandardLocation.CLASS_OUTPUT;

public class JsonSchemaGenerator extends AbstractK8sGenerator {

    public JsonSchemaGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    private boolean flowDefCreated = false;
    private final Schema schema = new Schema("membrane");

    private static final String[] excludeFromFlow = {
            "httpClient",
            "ruleMatching",
            "wadlRewriter",
            "global",
            "exchangeStore",
            "accountRegistration",
            "userFeature",
            "tcp",
            "wsaEndpointRewriter",
            "flowInitiator",
            "kubernetesValidation",
            "dispatching",
            "groovyTemplate"
    };

    public void write(Model m) throws IOException {
        for (MainInfo main : m.getMains()) {
            assemble(m, main);
        }
    }

    private void assemble(Model m, MainInfo main) throws IOException {

        List<RefObj> oneOfArray = new ArrayList<>();

        for (ElementInfo elementInfo : main.getElements().values()) {

            if (elementInfo.getAnnotation().mixed() && !elementInfo.getChildElementSpecs().isEmpty()) {
                throw new ProcessingException(
                        "@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
                        elementInfo.getElement()
                );
            }

            SchemaObject parser = createParser(m, main, elementInfo);

            // Only generate JSON for api
            if (elementInfo.getAnnotation().topLevel() && "com.predic8.membrane.core.openapi.serviceproxy.APIProxy".equals(elementInfo.getElement().getQualifiedName().toString())) {
                SchemaObject envelope = createEnvelopeSchema(m, elementInfo, parser);
                schema.addDefinition(envelope);
                oneOfArray.add(new RefObj("#/$defs/" + envelope.getName()));
            }

            schema.addDefinition(parser);
        }

        // Temporary fallback to 'anyOf' since several YAMLs lack a 'kind' field.
        // 'anyOf' allows multiple matching schemas and may hide config errors that 'oneOf' would detect.
        // TODO: Revert to 'oneOf' once missing 'kind' cases are resolved.
        schema.addAttribute("oneOf", oneOfArray);

        writeSchema(main, schema);
    }

    private SchemaObject createParser(Model m, MainInfo main, ElementInfo elementInfo) {
        SchemaObject parser = new SchemaObject(elementInfo.getXSDTypeName(m));
        parser.addAttribute("type", elementInfo.getAnnotation().noEnvelope() ? "array" : "object");
        parser.addAttribute("additionalProperties", false);
        //      parser.addAttribute("description", getDescriptionAsText(elementInfo));
        //       parser.addAttribute("x-intellij-html-description", getDescriptionAsHtml(elementInfo));
        collectProperties(m, main, elementInfo, parser);
        return parser;
    }

    private static SchemaObject createEnvelopeSchema(Model m, ElementInfo elementInfo, SchemaObject parser) {
        SchemaObject env = new SchemaObject(elementInfo.getXSDTypeName(m).replaceFirst("Parser$", "Envelope"));

        env.addAttribute("additionalProperties", false);
        env.addAttribute("required", List.of("\"spec\""));
        env.addProperty(
                new SchemaObject("apiVersion") {{
                    addAttribute("type", "string");
                }});
        env.addProperty(new SchemaObject("kind") {{
            addAttribute("type", "string");
            addAttribute("enum", List.of("\"" + elementInfo.getAnnotation().name() + "\""));
        }});
        env.addProperty(new SchemaObject("metadata") {{
            addAttribute("type", "object");
        }});
        env.addProperty(new SchemaObject("spec") {{
            addAttribute("$ref", "#/$defs/" + parser.getName());
        }});
        return env;
    }

    private void writeSchema(MainInfo main, Schema schema) throws IOException {
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

    private FileObject createFile(MainInfo main) throws IOException {
        List<Element> sources = new ArrayList<>(main.getInterceptorElements());
        sources.add(main.getElement());
        return processingEnv.getFiler()
                .createResource(
                        CLASS_OUTPUT,
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
                    //             sop.addAttribute("description", getDescriptionAsText(ai));
                    //             sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(ai));
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
        //       sop.addAttribute("description", getDescriptionAsText(i));
        //       sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(i));
        sop.addAttribute("type", "string");
        so.addProperty(sop);
    }

    private void collectChildElements(Model m, MainInfo main, ElementInfo i, ISchema so) {
        for (ChildElementInfo cei : i.getChildElementSpecs()) {

            ISchema parent2 = so;

            if (cei.isList()) {
                if ("flow".equals(cei.getPropertyName())) {
                    var sos = new ArrayList<SchemaObject>();
                    for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
                        if (!filter(cei.getPropertyName(), ei.getAnnotation().name()))
                            continue;
                        var sop = new SchemaObject(null);
                        sop.addAttribute("type", "object");
                        sop.addAttribute("additionalProperties", false);
                        SchemaObject prop = new SchemaObject(ei.getAnnotation().name());
                        prop.addAttribute("$ref", "#/$defs/" + ei.getXSDTypeName(m));
                        sop.addProperty(prop);
                        sos.add(sop);
                    }
                    processList(i, so, cei, sos);
                    continue;
                }
                parent2 = processList(i, so, cei, null);
            } else {
                if (cei.getAnnotation().allowForeign()) {
                    var ref = new SchemaObject("$ref");
                    ref.addAttribute("type", "string");
                    parent2.addProperty(ref);
                }
            }
            addChildsAsProperties(m, main, cei, parent2);
        }
    }

    private ISchema processList(ElementInfo i, ISchema so, ChildElementInfo cei, ArrayList<SchemaObject> sos) {

        SchemaObject items = new SchemaObject("items");

        if ("flow".equals(cei.getPropertyName())) {
            SchemaObject oF = processFlowElement(so, cei, sos);
            if (oF != null) return oF;
            return items;
        }

        items.addAttribute("type", "object");
        items.addAttribute("additionalProperties", cei.getAnnotation().allowForeign());

        if (i.getAnnotation().noEnvelope()) {
            so.addAttribute("items", items);
        } else {
            SchemaObject sop = createFromChild(cei, items);
            so.addProperty(sop);
        }

        return items;
    }

    private SchemaObject processFlowElement(ISchema so, ChildElementInfo cei, ArrayList<SchemaObject> sos) {
        if (!flowDefCreated) {
            SchemaObject sop1 = new SchemaObject(cei.getPropertyName());
            sop1.addAttribute("type", "array");

            var oF = new SchemaObject("items");
            oF.addAttribute("anyOf", new ArrayList<>(sos));

            sop1.addAttribute("items", oF);
            schema.addDefinition(sop1);
            flowDefCreated = true;
            return oF;
        }
        SchemaObject flow = new SchemaObject("flow");
        flow.addAttribute("$ref", "#/$defs/flow");
        so.addProperty(flow);
        return null;
    }

    private static void addChildsAsProperties(Model m, MainInfo main, ChildElementInfo cei, ISchema parent2) {
        for (ElementInfo ei : getChildElementDeclarations(main).get(cei.getTypeDeclaration()).getElementInfo()) {
            SchemaObject sop = new SchemaObject(ei.getAnnotation().name());
            //       sop.addAttribute("description", getDescriptionAsText(ei));
            //        sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(ei));
            sop.setRequired(cei.isRequired());
            sop.addAttribute("$ref", "#/$defs/" + ei.getXSDTypeName(m));
            parent2.addProperty(sop);
        }
    }

    private static Map<TypeElement, ChildElementDeclarationInfo> getChildElementDeclarations(MainInfo main) {
        return main.getChildElementDeclarations();
    }

    private static boolean filter(String objectName, String propertyName) {
        if (!objectName.equals("flow"))
            return true;
        for (String exclude : excludeFromFlow) {
            if (exclude.equals(propertyName))
                return false;
        }
        return true;
    }

    private SchemaObject createFromChild(ChildElementInfo cei, SchemaObject items) {
        SchemaObject sop = new SchemaObject(cei.getPropertyName());
        sop.setRequired(cei.isRequired());
        //     sop.addAttribute("description", getDescriptionAsText(cei));
        //       sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(cei));
        sop.addAttribute("type", "array");
        //   sop.addAttribute("additionalItems", false); // Not 2020-12
        sop.addAttribute("items", items);
        return sop;
    }

    @Override
    protected String fileName() {
        return "";
    }

    private static String escapeJsonContent(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(escape(c));
        }
        return sb.toString();
    }

    public static String escape(char c) {
        return switch (c) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\b' -> "\\b";
            case '\f'-> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> {
                if (c < 0x20) yield String.format("\\u%04x", (int) c);
                else yield String.valueOf(c);
            }
        };
    }

    private String getDescriptionAsText(AbstractJavadocedInfo elementInfo) {
        return escapeJsonContent(getDescriptionContent(elementInfo).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim());
    }

    private String getDescriptionAsHtml(AbstractJavadocedInfo elementInfo) {
        return escapeJsonContent(getDescriptionContent(elementInfo).replaceAll("\\s+", " ").trim());
    }

}
