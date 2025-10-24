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

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaObject.string;
import static javax.tools.StandardLocation.*;

/**
 * TODOs:
 * - A required property with a base type needs all the subtypes to be present in the schema. See CacheParser
 * - ports are strings
 */
public class JsonSchemaGenerator extends AbstractK8sGenerator {

    private Map<String,Boolean> topLevelAdded = new HashMap<>();

    public JsonSchemaGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    private boolean flowDefCreated = false;
    private Schema schema;

    private static final Set<String> excludeFromFlow = Set.of(
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
            "groovyTemplate",
            "adminApi"
    );

    public void write(Model m) throws IOException {
        for (MainInfo main : m.getMains()) {
            assemble(m, main);
        }
    }

    private void assemble(Model m, MainInfo main) throws IOException {
        // Reset so multiple calls would be possible
        flowDefCreated = false;
        schema = new Schema("membrane");

        for (ElementInfo elementInfo : main.getElements().values()) {

            if (elementInfo.getAnnotation().mixed() && !elementInfo.getChildElementSpecs().isEmpty()) {
                throw new ProcessingException(
                        "@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
                        elementInfo.getElement()
                );
            }

            SchemaObject parser = createParser(m, main, elementInfo);

            schema.addDefinition(parser);
        }
        schema.addProperty(string("kind").enumeration(List.of("api")));
        schema.addProperty(new SchemaObject("spec").ref("#/$defs/com.predic8.membrane.core.config.spring.ApiParser"));
        schema.required(List.of("\"spec\""));

        writeSchema(main, schema);
    }

    private SchemaObject createParser(Model m, MainInfo main, ElementInfo elementInfo) {
        SchemaObject parser = new SchemaObject(elementInfo.getXSDTypeName(m));

        // e.g. to prevent a request from needing a flow child noEnvelope=true is used
        if (elementInfo.getAnnotation().noEnvelope()) {
            // With noEnvelope=true, there should be exactly one child element
            var childName = elementInfo.getChildElementSpecs().get(0).getPropertyName();
            parser.ref("#/$defs/%sParser".formatted(childName));

            if (!topLevelAdded.containsKey(childName) && !"flow".equals(childName)) {
                SchemaArray array = new SchemaArray(childName + "Parser");
                collectChildElements(m, main, elementInfo.getChildElementSpecs().get(0).getEi(), array);
                schema.addDefinition(array);
                topLevelAdded.put(childName, true);
            }

            return parser;
        } else {
            parser.type("object").additionalProperties( false);
        }

        //      parser.addAttribute("description", getDescriptionAsText(elementInfo));
        //       parser.addAttribute("x-intellij-html-description", getDescriptionAsHtml(elementInfo));
        collectProperties(m, main, elementInfo, parser);
        return parser;
    }

    private static SchemaObject createEnvelopeSchema(Model m, ElementInfo elementInfo, SchemaObject parser) {
        SchemaObject env = new SchemaObject(elementInfo.getXSDTypeName(m).replaceFirst("Parser$", "Envelope"));

        env.type("object")
            .additionalProperties(false)
            .required(List.of("\"spec\"")); // TODO
        env.addProperty( string("apiVersion"));
        env.addProperty(string("kind").enumeration( List.of(List.of(elementInfo.getAnnotation().name())));
        env.addProperty(new SchemaObject("metadata").type("object"));
        env.addProperty(new SchemaObject("spec").ref( "#/$defs/" + parser.getName()));
        return env;
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

    private void collectAttributes(ElementInfo i, SchemaObject so) {
        i.getAis().stream()
                .filter(ai -> !ai.getXMLName().equals("id"))
                .forEach(ai -> {
                    SchemaObject sop = new SchemaObject(ai.getXMLName());
                    //             sop.addAttribute("description", getDescriptionAsText(ai));
                    //             sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(ai));
                    sop.type(ai.getSchemaType(processingEnv.getTypeUtils()));
                    sop.setRequired(ai.isRequired());
                    so.addProperty(sop);
                });
    }

    private void collectProperties(Model m, MainInfo main, ElementInfo i, SchemaObject schema) {
        collectAttributes(i, schema);
        collectTextContent(i, schema);
        collectChildElements(m, main, i, schema);
    }

    private void collectTextContent(ElementInfo i, SchemaObject so) {
        if (i.getTci() == null)
            return;

        SchemaObject sop = string(i.getTci().getPropertyName());
        //       sop.addAttribute("description", getDescriptionAsText(i));
        //       sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(i));
        so.addProperty(sop);
    }

    private void collectChildElements(Model m, MainInfo main, ElementInfo i, AbstractSchema so) {
        for (ChildElementInfo cei : i.getChildElementSpecs()) {

            AbstractSchema parent2 = so;

            if (cei.isList()) {
                if ("flow".equals(cei.getPropertyName())) {
                    var sos = new ArrayList<SchemaObject>();
                    for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
                        if (!filter(cei.getPropertyName(), ei.getAnnotation().name()))
                            continue;
                        SchemaObject s = new SchemaObject(null)
                                .type( "object")
                                .additionalProperties(false);

                        s.addProperty(new SchemaObject(ei.getAnnotation().name()).ref("#/$defs/" + ei.getXSDTypeName(m)));
                        sos.add(s);
                    }
                    processList(i, so, cei, sos);
                    continue;
                }
                parent2 = processList(i, so, cei, null);
            } else {
                // Check if we need a $ref or if it is allowed everywhere
                if (cei.getAnnotation().allowForeign()) {
                    // parent2.addProperty(new SchemaObject("$ref").attribute("type", "string"));
                }
            }
            addChildsAsProperties(m, main, cei, (SchemaObject) parent2);
        }
    }

    private AbstractSchema processList(ElementInfo i, AbstractSchema so, ChildElementInfo cei, ArrayList<SchemaObject> sos) {

        SchemaObject items = new SchemaObject("items");

        if ("flow".equals(cei.getPropertyName())) {
            processFlowElement((SchemaObject) so, sos);
            return items;
        }

        items.type("object").additionalProperties(cei.getAnnotation().allowForeign());

        if (i.getAnnotation().noEnvelope() && so instanceof SchemaArray sa) {
            sa.items(items);
        } else {
            if (so instanceof SchemaObject sObj) {
                sObj.addProperty(createFromChild(cei, items));
            }
        }

        return items;
    }

    private void processFlowElement(SchemaObject so, ArrayList<SchemaObject> sos) {
        if (!flowDefCreated) {
            SchemaArray flow = new SchemaArray("flowParser");
            var items = new SchemaObject("items")
                    .attribute("anyOf", new ArrayList<>(sos));
            flow.items(items);
            schema.addDefinition(flow);
            flowDefCreated = true;
        }
        so.addProperty(new SchemaObject("flow").ref("#/$defs/flowParser"));
    }

    private static void addChildsAsProperties(Model m, MainInfo main, ChildElementInfo cei, SchemaObject parent2) {
        for (ElementInfo ei : getChildElementDeclarations(main).get(cei.getTypeDeclaration()).getElementInfo()) {
            SchemaObject anno = new SchemaObject(ei.getAnnotation().name());
            //       sop.addAttribute("description", getDescriptionAsText(ei));
            //        sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(ei));
            anno.setRequired(cei.isRequired());
            anno.ref("#/$defs/" + ei.getXSDTypeName(m));
            parent2.addProperty(anno);
        }
    }

    private static Map<TypeElement, ChildElementDeclarationInfo> getChildElementDeclarations(MainInfo main) {
        return main.getChildElementDeclarations();
    }

    private static boolean filter(String objectName, String propertyName) {
        if (!objectName.equals("flow"))
            return true;
        return !excludeFromFlow.contains(propertyName);
    }

    private SchemaArray createFromChild(ChildElementInfo cei, SchemaObject items) {
        SchemaArray prop = new SchemaArray(cei.getPropertyName()).items(items);
        prop.setRequired(cei.isRequired());
        //     sop.addAttribute("description", getDescriptionAsText(cei));
        //       sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(cei));
        //   sop.addAttribute("additionalItems", false); // Not 2020-12
        return prop;
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
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> {
                if (c < 0x20) yield String.format("\\u%04x", (int) c);
                else yield String.valueOf(c);
            }
        };
    }

    private void writeSchema(MainInfo main, Schema schema) throws IOException {
        try (BufferedWriter w = new BufferedWriter(createFile(main).openWriter())) {
            w.write(schema.toString());
        }
    }

    // For description. Probably we'll include that later. (Temporarily deactivated!)
    private String getDescriptionAsText(AbstractJavadocedInfo elementInfo) {
        return escapeJsonContent(getDescriptionContent(elementInfo).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim());
    }

    // For description. Probably we'll include that later. (Temporarily deactivated!
    private String getDescriptionAsHtml(AbstractJavadocedInfo elementInfo) {
        return escapeJsonContent(getDescriptionContent(elementInfo).replaceAll("\\s+", " ").trim());
    }
}