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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
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

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.*;
import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.ref;
import static com.predic8.membrane.annot.generator.util.SchemaGeneratorUtil.*;
import static javax.tools.StandardLocation.*;

/**
 * TODOs:
 * - A required property with a base type needs all the subtypes to be present in the schema. See CacheParser
 * - ports are strings
 * - Choose/cases/case has one nesting to much
 */
public class JsonSchemaGenerator extends AbstractK8sGenerator {

    private final ObjectMapper om = new ObjectMapper();
    private ObjectWriter writer = om.writerWithDefaultPrettyPrinter();

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
        schema = schema("membrane");
        topLevelAdded.clear();

        for (ElementInfo elementInfo : main.getElements().values()) {

            if (elementInfo.getAnnotation().mixed() && !elementInfo.getChildElementSpecs().isEmpty()) {
                throw new ProcessingException(
                        "@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
                        elementInfo.getElement()
                );
            }

            schema.definition(createParser(m, main, elementInfo));
        }
        schema.additionalProperties(false)
            .property( string("kind").enumeration(List.of("api")))
            .property( ref("spec").ref("#/$defs/com.predic8.membrane.core.config.spring.ApiParser").required(true));

        writeSchema(main, schema);
    }

    private SchemaObject createParser(Model m, MainInfo main, ElementInfo elementInfo) {
        String name = elementInfo.getXSDTypeName(m);

        // e.g. to prevent a request from needing a flow child noEnvelope=true is used
        if (elementInfo.getAnnotation().noEnvelope()) {
            // With noEnvelope=true, there should be exactly one child element

            var childName = elementInfo.getChildElementSpecs().getFirst().getPropertyName();

            if (!topLevelAdded.containsKey(childName) && !"flow".equals(childName)) {
                SchemaArray array = array(childName + "Parser");
                collectChildElements(m, main, elementInfo.getChildElementSpecs().getFirst().getEi(), array);
                schema.definition(array);
                topLevelAdded.put(childName, true);
            }

            return ref(name).ref("#/$defs/%sParser".formatted(childName));
        }

        SchemaObject parser = object(name).additionalProperties( false)
            .description( getDescriptionContent(elementInfo));
        collectProperties(m, main, elementInfo, parser);
        return parser;
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
                .forEach(ai -> so.property(object(ai.getXMLName())
                        .description(getDescriptionContent(ai))
                        .type(ai.getSchemaType(processingEnv.getTypeUtils()))
                        .required(ai.isRequired())));
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
        so.property(sop);
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
                        sos.add(object()
                                .additionalProperties(false)
                                .property(ref(ei.getAnnotation().name()).ref("#/$defs/" + ei.getXSDTypeName(m))));
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

        SchemaObject items = object("items");

        if ("flow".equals(cei.getPropertyName())) {
            processFlowElement((SchemaObject) so, sos);
            return items;
        }

        items.type("object").additionalProperties(cei.getAnnotation().allowForeign());

        if (i.getAnnotation().noEnvelope() && so instanceof SchemaArray sa) {
            sa.items(items);
        } else {
            if (so instanceof SchemaObject sObj) {
                sObj.property(createFromChild(cei, items));
            }
        }

        return items;
    }

    private void processFlowElement(SchemaObject so, List<SchemaObject> sos) {
        if (!flowDefCreated) {
            schema.definition( array("flowParser").items( anyOf(sos)));
            flowDefCreated = true;
        }
        so.property(ref("flow").ref("#/$defs/flowParser"));
    }

    private void addChildsAsProperties(Model m, MainInfo main, ChildElementInfo cei, SchemaObject parent2) {
        for (ElementInfo ei : getChildElementDeclarations(main).get(cei.getTypeDeclaration()).getElementInfo()) {
            parent2.property(ref(ei.getAnnotation().name())
                .ref("#/$defs/" + ei.getXSDTypeName(m)))
                .description(getDescriptionContent(ei))
                .required(cei.isRequired());

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
        return array(cei.getPropertyName())
                .items(items)
                .required(cei.isRequired())
                .description(getDescriptionContent(cei));
    }

    @Override
    protected String fileName() {
        return "";
    }

    private void writeSchema(MainInfo main, Schema schema) throws IOException {
        try (BufferedWriter w = new BufferedWriter(createFile(main).openWriter())) {
            String prettyJson = writer.writeValueAsString(schema.json(JsonNodeFactory.instance.objectNode()));
            w.write(prettyJson);
            System.out.println(prettyJson);
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