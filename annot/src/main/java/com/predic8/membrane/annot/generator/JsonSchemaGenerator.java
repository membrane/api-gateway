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

import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.annot.generator.kubernetes.*;
import com.predic8.membrane.annot.generator.kubernetes.model.*;
import com.predic8.membrane.annot.model.*;
import com.predic8.membrane.annot.model.doc.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.tools.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.*;
import static com.predic8.membrane.annot.generator.util.SchemaGeneratorUtil.*;
import static javax.tools.StandardLocation.*;

/**
 * TODOs:
 * - A required property with a base type needs all the subtypes to be present in the schema. See CacheParser
 * - ports are strings
 * - Choose/cases/case has one nesting to much
 * - apiKey/extractors/expressionExtractor/expression => too much?
 */
public class JsonSchemaGenerator extends AbstractGrammar {

    public static final String MEMBRANE_SCHEMA_JSON_FILENAME = "membrane.schema.json";

    private final Map<String, Boolean> componentAdded = new HashMap<>();

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
        componentAdded.clear();

        addParserDefinitions(m, main);
        addTopLevelProperties(m, main);

        writeSchema(main, schema);
    }

    // Uses rootDef to define the elements configurable at top-level
    private void addTopLevelProperties(Model m, MainInfo main) {
        schema.additionalProperties(false);
        List<AbstractSchema<?>> kinds = new ArrayList<>();

        main.getElements().values().stream().filter(e -> e.getAnnotation().topLevel()).forEach(e -> {

            String name = e.getAnnotation().name();
            String refName = "#/$defs/" + e.getXSDTypeName(m);

            schema.property(ref(name).ref(refName));

            kinds.add(object()
                    .additionalProperties(false)
                    .property(ref(name)
                            .ref(refName)
                            .required(true)));
        });

        if (!kinds.isEmpty())
            schema.oneOf(kinds);
    }

    private void addParserDefinitions(Model m, MainInfo main) {
        for (ElementInfo elementInfo : main.getElements().values()) {

            if (elementInfo.getAnnotation().mixed() && !elementInfo.getChildElementSpecs().isEmpty()) {
                throw new ProcessingException(
                        "@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
                        elementInfo.getElement()
                );
            }

            schema.definition(createParser(m, main, elementInfo));
        }

        // Additional component-only parsers (id allowed only in components-list context)
        for (ElementInfo elementInfo : main.getElements().values()) {
            if (!elementInfo.getAnnotation().component())
                continue;

            ensureValidIdSetter(elementInfo);
            schema.definition(createComponentParser(m, main, elementInfo));
        }
    }


    private SchemaObject createParser(Model m, MainInfo main, ElementInfo elementInfo) {
        String parserName = elementInfo.getXSDTypeName(m);

        // e.g. to prevent a request from needing a flow child noEnvelope=true is used
        if (elementInfo.getAnnotation().noEnvelope()) {
            // With noEnvelope=true, there should be exactly one child element
            ChildElementInfo child = elementInfo.getChildElementSpecs().getFirst();
            var childName = child.getPropertyName();

            if (!componentAdded.containsKey(childName) && !shouldGenerateFlowParserType(child)) {
                SchemaArray array = array(childName + "Parser");
                processMCChilds(m, main, child.getEi(), array);
                schema.definition(array);
                componentAdded.put(childName, true);
            }

            return ref(parserName).ref("#/$defs/%sParser".formatted(childName));
        }

        SchemaObject parser = object(parserName)
                .additionalProperties(elementInfo.getOai() != null)
                .description(getDescriptionContent(elementInfo));
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
                        main.getAnnotation().outputPackage().replaceAll("\\.spring$", ".json"),
                        MEMBRANE_SCHEMA_JSON_FILENAME,
                        sources.toArray(new Element[0])
                );
    }

    private void processMCAttributes(ElementInfo i, SchemaObject so) {
        i.getAis().forEach(ai -> {

            // skip attributes marked with @MCExcludeFromSchema
            if (ai.excludedFromJsonSchema())
                return;

            so.property(createProperty(ai));
        });
    }

    private AbstractSchema<?> createProperty(AttributeInfo ai) {
        String type = ai.getSchemaType(processingEnv.getTypeUtils());
        AbstractSchema<?> s = SchemaFactory.from(type)
                .name(ai.getXMLName())
                .description(getDescriptionContent(ai))
                .type(type)
                .required(ai.isRequired());
        // Add enum values if the type is an enum. If it is an enum for a boolean value, rely on the "boolean" type.
        if (ai.isEnum(processingEnv.getTypeUtils()) && !"boolean".equals(type)) {
            s.enumValues(ai.enumsAsLowerCaseList(processingEnv.getTypeUtils()));
        }
        return s;
    }


    private void collectProperties(Model m, MainInfo main, ElementInfo i, SchemaObject schema) {
        processMCAttributes(i, schema);
        collectTextContent(i, schema);
        processMCChilds(m, main, i, schema);
    }

    private void collectTextContent(ElementInfo i, SchemaObject so) {
        if (i.getTci() == null)
            return;

        var sop = string(i.getTci().getPropertyName());
        //       sop.addAttribute("description", getDescriptionAsText(i));
        //       sop.addAttribute("x-intellij-html-description", getDescriptionAsHtml(i));
        so.property(sop);
    }

    private void processMCChilds(Model m, MainInfo main, ElementInfo i, AbstractSchema<?> so) {
        for (ChildElementInfo cei : i.getChildElementSpecs()) {

            // TODO has to be in the schema. Otherwise the schema validation will fail
//            if (cei.excludedFromJsonSchema())
//                return;

            AbstractSchema<?> parent2 = so;

            if (cei.isList()) {
                if (shouldGenerateFlowParserType(cei)) {
                    var sos = new ArrayList<SchemaObject>();

                    for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
                        if (excludeFromFlow.contains(ei.getAnnotation().name()))
                            continue;

                        String defName = ei.getXSDTypeName(m);

                        sos.add(object()
                                .additionalProperties(false)
                                .property(ref(ei.getAnnotation().name())
                                        .ref("#/$defs/" + defName)
                                        .required(true)));
                    }
                    // Allow referencing a component instance directly on list-item level:
                    // flow:
                    //   - $ref: ...
                    sos.add(object()
                            .additionalProperties(false)
                            .property(string("$ref").required(true)));

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

            addChildsAsProperties(m, main, cei, (SchemaObject) parent2, isComponentsList(i, cei), cei.isList());
        }
    }

    private boolean isComponentsList(ElementInfo parent, ChildElementInfo cei) {
        return "components".equals(parent.getAnnotation().name())
                && parent.getAnnotation().noEnvelope()
                && "components".equals(cei.getPropertyName());
    }

    private boolean shouldGenerateFlowParserType(ChildElementInfo cei) {
        return "flow".equals(cei.getPropertyName()) && !isFlowFromWebSocket(cei);
    }

    boolean isFlowFromWebSocket(ChildElementInfo cei) {
        // String had to be used cause for class we need dependency to core that is otherwise not needed.
        return "com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface".equals(cei.getTypeDeclaration().getQualifiedName().toString());
    }

    private AbstractSchema<?> processList(ElementInfo i, AbstractSchema<?> so, ChildElementInfo cei, ArrayList<SchemaObject> sos) {
        SchemaObject items = object("items");

        if (shouldGenerateFlowParserType(cei)) {
            addFlowParserRef(so, sos);
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

    private void addFlowParserRef(AbstractSchema<?> so, List<SchemaObject> sos) {
        if (!flowDefCreated) {
            schema.definition(array("flowParser").items(anyOf(sos)));
            flowDefCreated = true;
        }
        SchemaRef ref = ref("flow").ref("#/$defs/flowParser");
        if (so instanceof SchemaArray sa) {
            sa.items(ref);
        } else if (so instanceof SchemaObject sObj) {
            sObj.property(ref);
        }
    }

    private void addChildsAsProperties(Model m, MainInfo main, ChildElementInfo cei, SchemaObject parent2, boolean componentsContext, boolean listItemContext) {
        var eis = getChildElementDeclarationInfo(main, cei).getElementInfo();


        // Generic list-item reference support:
        // If this list can contain at least one @MCElement(component=true) type,
        // allow "- $ref: ..." as an alternative list item shape.
        if (listItemContext && !componentsContext && eis.stream().anyMatch(ei -> ei.getAnnotation().component())) {
            parent2.property(string("$ref").required(false));
        }

        for (ElementInfo ei : eis) {
            String defName = ei.getXSDTypeName(m);

            // Only the components-list gets the id-augmented schema
            if (componentsContext && ei.getAnnotation().component()) {
                defName = componentDefName(defName);
            }

            parent2.property(ref(ei.getAnnotation().name())
                            .ref("#/$defs/" + defName))
                    .description(getDescriptionContent(ei))
                    .required(cei.isRequired());
        }
    }

    private static ChildElementDeclarationInfo getChildElementDeclarationInfo(MainInfo main, ChildElementInfo cei) {
        return getChildElementDeclarations(main).get(cei.getTypeDeclaration());
    }

    private static Map<TypeElement, ChildElementDeclarationInfo> getChildElementDeclarations(MainInfo main) {
        return main.getChildElementDeclarations();
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
            w.write(writer.writeValueAsString(schema.json(JsonNodeFactory.instance.objectNode())));
        }
    }

    private AbstractSchema<?> createComponentParser(Model m, MainInfo main, ElementInfo elementInfo) {
        String baseName = elementInfo.getXSDTypeName(m);
        String compName = componentDefName(baseName);

        if (elementInfo.getAnnotation().noEnvelope()) {
            return ref(compName).ref("#/$defs/" + baseName);
        }

        // If it already has a real id attribute, just alias the base schema
        if (hasRealIdAttribute(elementInfo)) {
            return ref(compName).ref("#/$defs/" + baseName);
        }

        SchemaObject parser = object(compName)
                .additionalProperties(elementInfo.getOai() != null)
                .description(getDescriptionContent(elementInfo));

        collectProperties(m, main, elementInfo, parser);

        parser.property(string("id").required(false));

        return parser;
    }


    private static String componentDefName(String baseDefName) {
        return baseDefName + "Component";
    }

    private boolean hasRealIdAttribute(ElementInfo elementInfo) {
        return elementInfo.getAis().stream().anyMatch(ai -> "id".equals(ai.getXMLName()));
    }


    /**
     * Ensures that:
     * - every @MCElement either has no setId(...) at all OR
     * - exactly one void setId(String) method annotated with @MCAttribute(name="id" or default)
     */
    private void ensureValidIdSetter(ElementInfo elementInfo) {
        if (!(elementInfo.getElement() instanceof TypeElement type)) return;

        var elements = processingEnv.getElementUtils();

        List<ExecutableElement> idSetters = new ArrayList<>();

        for (Element e : elements.getAllMembers(type)) {
            if (e.getKind() != ElementKind.METHOD)
                continue;
            ExecutableElement m = (ExecutableElement) e;
            if (!m.getSimpleName().contentEquals("setId"))
                continue;

            if (m.getParameters().size() != 1
                    || m.getReturnType().getKind() != TypeKind.VOID
                    || !processingEnv.getTypeUtils().isSameType(
                    m.getParameters().getFirst().asType(),
                    elements.getTypeElement("java.lang.String").asType()
            )) {
                throw new ProcessingException("setId(...) on %s must be exactly 'void setId(String)'.".formatted(type.getQualifiedName()), m);
            }
            idSetters.add(m);
        }

        if (idSetters.isEmpty())
            return;  // no setId(String) present => OK

        if (idSetters.size() > 1)
            throw new ProcessingException("Multiple setId(String) methods found on " + type.getQualifiedName(), idSetters.getFirst());

        ExecutableElement setId = idSetters.getFirst();
        MCAttribute attr = setId.getAnnotation(MCAttribute.class);
        if (attr == null) {
            throw new ProcessingException("setId(String) on " + type.getQualifiedName() + " must be annotated with @MCAttribute(name=\"id\").", setId);
        }

        String attrName = attr.attributeName().isEmpty() ? "id" : attr.attributeName();
        if (!"id".equals(attrName)) {
            throw new ProcessingException("setId(String) on " + type.getQualifiedName() + " must use @MCAttribute(name=\"id\") or default name \"id\", but is \"" + attrName + "\".", setId);
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