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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.predic8.membrane.annot.ProcessingException;
import com.predic8.membrane.annot.generator.kubernetes.AbstractGrammar;
import com.predic8.membrane.annot.generator.kubernetes.model.*;
import com.predic8.membrane.annot.model.*;
import com.predic8.membrane.annot.model.doc.Doc;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.*;
import static com.predic8.membrane.annot.generator.util.SchemaGeneratorUtil.escapeJsonContent;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

/**
 * TODOs:
 * - A required property with a base type needs all the subtypes to be present in the schema. See CacheParser
 * - ports are strings
 * - Choose/cases/case has one nesting to much
 * - apiKey/extractors/expressionExtractor/expression => too much?
 */
public class JsonSchemaGenerator extends AbstractGrammar {

    public static final String MEMBRANE_SCHEMA_JSON_FILENAME = "membrane.schema.json";
    public static final String COMPONENTS = "components";
    private static final String INTERCEPTOR_FQN = "com.predic8.membrane.core.interceptor.Interceptor";
    private static final String JSON_SCHEMA_DEFS_PREFIX = "#/$defs/";
    private static final String $_REF = "$ref";
    private static final String FLOW_PARSER_DEF_NAME = "flowParser";

    // TODO keep this pattern or allow *?
    public static final String COMPONENT_ID_PATTERN = "^[A-Za-z_][A-Za-z0-9_-]*$";

    private final Set<String> componentParsersAdded = new HashSet<>();

    private boolean flowDefCreated = false;
    private Schema schema;

    public JsonSchemaGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    public void write(Model m) throws IOException {
        for (MainInfo main : m.getMains()) {
            assemble(m, main);
        }
    }

    private void assemble(Model m, MainInfo main) throws IOException {
        // Reset so multiple calls would be possible
        flowDefCreated = false;
        schema = schema("membrane");
        componentParsersAdded.clear();

        addParserDefinitions(m, main);
        addTopLevelProperties(m, main);

        writeSchema(main, schema);
    }

    private void addTopLevelProperties(Model m, MainInfo main) {
        schema.additionalProperties(false);

        var top = main.getElements().values().stream()
                .filter(e -> e.getAnnotation().topLevel())
                .toList();

        for (ElementInfo e : top) {
            String name = e.getAnnotation().name();
            schema.property(defsSchemaRef(name, e.getXSDTypeName(m)));
        }

        if (!top.isEmpty()) {
            schema.minProperties(1).maxProperties(1);
        }
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
    }

    private AbstractSchema<?> createParser(Model model, MainInfo main, ElementInfo elementInfo) {
        String parserName = elementInfo.getXSDTypeName(model);

        if (isComponentsMap(elementInfo)) {
            return createComponentsMapParser(model, main, elementInfo, parserName);
        }

        if (elementInfo.getAnnotation().noEnvelope()) {
            return createNoEnvelopeParser(model, main, elementInfo, parserName);
        }

        // enforce the inline form for collapsed elements
        if (elementInfo.getAnnotation().collapsed()) {
            return createCollapsedParser(elementInfo, parserName);
        }

        return createRegularParser(model, main, elementInfo, parserName);
    }

    private AbstractSchema<?> createNoEnvelopeParser(Model model, MainInfo main, ElementInfo elementInfo, String parserName) {
        // With noEnvelope=true, there should be exactly one child element
        ChildElementInfo childSpec = elementInfo.getChildElementSpecs().getFirst();
        String childName = childSpec.getPropertyName();

        if (!componentParsersAdded.contains(childName) && !shouldGenerateFlowParserType(childSpec)) {
            SchemaArray childParserArray = array(childName + "Parser");
            processMCChilds(model, main, childSpec.getEi(), childParserArray);
            schema.definition(childParserArray);
            componentParsersAdded.add(childName);
        }

        if (shouldGenerateFlowParserType(childSpec)) {
            return ref(parserName).ref(defsRefPath(FLOW_PARSER_DEF_NAME));
        }

        return ref(parserName).ref(defsRefPath(childName + "Parser"));
    }

    private AbstractSchema<?> createCollapsedParser(ElementInfo elementInfo, String parserName) {
        if (elementInfo.getAnnotation().noEnvelope()) {
            throw new ProcessingException(
                    "@MCElement(collapsed=true) is not compatible with noEnvelope=true.",
                    elementInfo.getElement()
            );
        }
        return createCollapsedInlineParser(elementInfo, parserName);
    }

    private AbstractSchema<?> createRegularParser(Model model, MainInfo main, ElementInfo elementInfo, String parserName) {
        SchemaObject parserSchema = getParserSchemaObject(elementInfo, parserName);

        collectProperties(model, main, elementInfo, parserSchema);

        // Allow object-level component reference if any setter expects a component.
        if (hasComponentChild(elementInfo, main) && !parserSchema.hasProperty($_REF)) {
            parserSchema.property(string($_REF)
                    .description("JSON Pointer to a component.")
                    .required(false));
        }

        return parserSchema;
    }

    private AbstractSchema<?> createCollapsedInlineParser(ElementInfo ei, String parserName) {
        var attrs = ei.getAis().stream().toList();

        boolean hasText = ei.getTci() != null;
        boolean hasChildren = !ei.getChildElementSpecs().isEmpty();

        if (hasChildren) {
            throw new ProcessingException("@MCElement(collapsed=true) must not declare child elements.", ei.getElement());
        }
        if (hasText && !attrs.isEmpty()) {
            throw new ProcessingException("@MCElement(collapsed=true) must not mix @MCTextContent with @MCAttribute.", ei.getElement());
        }

        // collapsed via single @MCTextContent -> scalar string
        if (hasText) {
            return SchemaFactory.from("string")
                    .name(parserName)
                    .type("string")
                    .description(getDescriptionContent(ei));
        }

        // collapsed via a single @MCAttribute-> scalar of that attribute type
        if (attrs.size() == 1) {
            AttributeInfo ai = attrs.getFirst();
            String type = ai.getSchemaType(processingEnv.getTypeUtils());

            AbstractSchema<?> s = SchemaFactory.from(type)
                    .name(parserName)
                    .typeAllowingString(type)
                    .description(getDescriptionContent(ai).isEmpty() ? getDescriptionContent(ei) : getDescriptionContent(ai));

            if (ai.isEnum(processingEnv.getTypeUtils()) && !"boolean".equals(type)) {
                s.enumValues(ai.enumsAsLowerCaseList(processingEnv.getTypeUtils()));
            }
            return s;
        }

        throw new ProcessingException(
                "@MCElement(collapsed=true) requires exactly one @MCAttribute or exactly one @MCTextContent.",
                ei.getElement()
        );
    }


    private SchemaObject getParserSchemaObject(ElementInfo elementInfo, String parserName) {
        return object(parserName)
                .additionalProperties(elementInfo.isString())
                .description(getDescriptionContent(elementInfo));
    }

    private boolean isComponentsMap(ElementInfo ei) {
        return COMPONENTS.equals(ei.getAnnotation().name()) && ei.isObject();
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
                        getOutputPackage(main),
                        MEMBRANE_SCHEMA_JSON_FILENAME,
                        sources.toArray(new Element[0])
                );
    }

    private static @NotNull String getOutputPackage(MainInfo main) {
        return main.getAnnotation().outputPackage().replaceAll("\\.spring$", ".json");
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
                .typeAllowingString(type)
                .required(ai.isRequired());
        // Add enum values if the type is an enum. If it is an enum for a boolean value, rely on the "boolean" type.
        if (ai.isEnum(processingEnv.getTypeUtils()) && !"boolean".equals(type)) {
            s.enumValues(ai.enumsAsLowerCaseList(processingEnv.getTypeUtils()));
        }
        return s;
    }


    private void collectProperties(Model model, MainInfo main, ElementInfo elementInfo, SchemaObject parserSchema) {
        processMCAttributes(elementInfo, parserSchema);
        collectTextContent(elementInfo, parserSchema);
        processMCChilds(model, main, elementInfo, parserSchema);
    }

    private void collectTextContent(ElementInfo elementInfo, SchemaObject parserSchema) {
        if (elementInfo.getTci() == null)
            return;

        var textProperty = string(elementInfo.getTci().getPropertyName());
        // textProperty.addAttribute("description", getDescriptionAsText(elementInfo));
        // textProperty.addAttribute("x-intellij-html-description", getDescriptionAsHtml(elementInfo));
        parserSchema.property(textProperty);
    }

    private void processMCChilds(Model model, MainInfo main, ElementInfo parentElementInfo, AbstractSchema<?> parentSchema) {
        for (ChildElementInfo childSpec : parentElementInfo.getChildElementSpecs()) {

            if (!childSpec.isList()) {
                addChildsAsProperties(model, main, childSpec, (SchemaObject) parentSchema, isComponentsList(parentElementInfo, childSpec), false);
                continue;
            }

            if (isScalarChildList(childSpec)) {
                attachArrayItems(parentElementInfo, parentSchema, childSpec, scalarItemsSchema(childSpec));
                continue;
            }

            if (shouldGenerateFlowParserType(childSpec)) {
                processList(parentElementInfo, parentSchema, childSpec, getSchemaObjects(model, main, childSpec));
                continue;
            }

            if (shouldInlineListItems(main, childSpec)) {
                processInlineList(model, main, parentElementInfo, parentSchema, childSpec);
                continue;
            }

            AbstractSchema<?> listItemObjectSchema = processList(parentElementInfo, parentSchema, childSpec, null);
            addChildsAsProperties(model, main, childSpec, (SchemaObject) listItemObjectSchema, isComponentsList(parentElementInfo, childSpec), true);
        }
    }

    private void processInlineList(Model model, MainInfo main, ElementInfo parentElementInfo, AbstractSchema<?> parentSchema, ChildElementInfo childSpec) {
        var childDeclaration = getChildElementDeclarationInfo(main, childSpec);

        ElementInfo itemElementInfo = childDeclaration.getElementInfo().stream()
                .filter(candidateElementInfo -> !candidateElementInfo.getAnnotation().topLevel())
                .findFirst()
                .orElseThrow(); // should never happen due to shouldInlineListItems

        AbstractSchema<?> itemsSchema = defsSchemaRef(itemElementInfo.getAnnotation().name(), itemElementInfo.getXSDTypeName(model));

        // keep "- $ref: ..." alternative for component items
        if (!isComponentsList(parentElementInfo, childSpec) && itemElementInfo.getAnnotation().component()) {
            var variants = new ArrayList<AbstractSchema<?>>();
            variants.add(itemsSchema);
            variants.add(object()
                    .title("componentRef")
                    .additionalProperties(false)
                    .property(string($_REF).required(false)));
            itemsSchema = anyOf(variants);
        }

        attachArrayItems(parentElementInfo, parentSchema, childSpec, itemsSchema);
    }

    private static @NotNull ArrayList<AbstractSchema<?>> getSchemaObjects(Model model, MainInfo main, ChildElementInfo childSpec) {
        var variants = new ArrayList<AbstractSchema<?>>();

        for (ElementInfo candidateElementInfo : main.getChildElementDeclarations().get(childSpec.getTypeDeclaration()).getElementInfo()) {
            if (candidateElementInfo.getAnnotation().excludeFromFlow())
                continue;

            variants.add(object()
                    .title(candidateElementInfo.getAnnotation().name())
                    .additionalProperties(false)
                    .property(defsSchemaRef(candidateElementInfo.getAnnotation().name(), candidateElementInfo.getXSDTypeName(model))));
        }

        // Allow referencing a component instance directly on list-item level:
        // flow:
        //   - $ref: ...
        variants.add(object()
                .title("componentRef")
                .additionalProperties(false)
                .property(string($_REF)));
        return variants;
    }

    private boolean isScalarChildList(ChildElementInfo cei) {
        if (!cei.isList()) return false;
        if (cei.getTypeDeclaration() == null) return false;

        String qn = cei.getTypeDeclaration().getQualifiedName().toString();

        return "java.lang.String".equals(qn)
                || "java.lang.Boolean".equals(qn)
                || "java.lang.Integer".equals(qn)
                || "java.lang.Long".equals(qn)
                || "java.lang.Double".equals(qn)
                || "java.lang.Float".equals(qn)
                || "java.lang.Short".equals(qn)
                || "java.lang.Byte".equals(qn);
    }

    private AbstractSchema<?> scalarItemsSchema(ChildElementInfo cei) {
        String qn = cei.getTypeDeclaration().getQualifiedName().toString();

        if ("java.lang.String".equals(qn)) {
            return SchemaFactory.from("string").type("string");
        }
        if ("java.lang.Boolean".equals(qn)) {
            return SchemaFactory.from("boolean").type("boolean");
        }
        if ("java.lang.Integer".equals(qn)
                || "java.lang.Long".equals(qn)
                || "java.lang.Short".equals(qn)
                || "java.lang.Byte".equals(qn)) {
            return SchemaFactory.from("integer").type("integer");
        }
        if ("java.lang.Double".equals(qn) || "java.lang.Float".equals(qn)) {
            return SchemaFactory.from("number").type("number");
        }

        return SchemaFactory.from("string").type("string");
    }

    private boolean isComponentsList(ElementInfo parentElementInfo, ChildElementInfo childSpec) {
        return COMPONENTS.equals(parentElementInfo.getAnnotation().name())
                && parentElementInfo.getAnnotation().noEnvelope()
                && COMPONENTS.equals(childSpec.getPropertyName());
    }

    private boolean shouldGenerateFlowParserType(ChildElementInfo cei) {
        if (!cei.isList()) return false;
        if (isFlowFromWebSocket(cei)) return false;

        TypeElement interceptor = processingEnv.getElementUtils().getTypeElement(INTERCEPTOR_FQN);
        if (interceptor == null) {
            return INTERCEPTOR_FQN.equals(cei.getTypeDeclaration().getQualifiedName().toString());
        }
        return processingEnv.getTypeUtils().isAssignable(cei.getTypeDeclaration().asType(), interceptor.asType());
    }

    boolean isFlowFromWebSocket(ChildElementInfo cei) {
        // String had to be used cause for class we need dependency to core that is otherwise not needed.
        return "com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface".equals(cei.getTypeDeclaration().getQualifiedName().toString());
    }

    private AbstractSchema<?> processList(ElementInfo parentElementInfo, AbstractSchema<?> parentSchema, ChildElementInfo childSpec, ArrayList<AbstractSchema<?>> itemVariants) {
        SchemaObject itemsObjectSchema = object("items");

        if (shouldGenerateFlowParserType(childSpec)) {
            addFlowParserRef(parentSchema, childSpec.getPropertyName(), itemVariants);
            return itemsObjectSchema;
        }

        itemsObjectSchema.type("object").additionalProperties(childSpec.getAnnotation().allowForeign());

        if (parentElementInfo.getAnnotation().noEnvelope() && parentSchema instanceof SchemaArray schemaArray) {
            schemaArray.items(itemsObjectSchema);
        } else {
            if (parentSchema instanceof SchemaObject schemaObject) {
                schemaObject.property(createFromChild(childSpec, itemsObjectSchema));
            }
        }

        return itemsObjectSchema;
    }

    private void addFlowParserRef(AbstractSchema<?> parentSchema, String propertyName, List<AbstractSchema<?>> itemVariants) {
        if (!flowDefCreated) {
            schema.definition(array(FLOW_PARSER_DEF_NAME).items(anyOf(itemVariants)));
            flowDefCreated = true;
        }
        SchemaRef flowParserRef = ref(propertyName).ref(defsRefPath(FLOW_PARSER_DEF_NAME));

        if (parentSchema instanceof SchemaArray schemaArray) {
            schemaArray.items(flowParserRef);
        } else if (parentSchema instanceof SchemaObject schemaObject) {
            schemaObject.property(flowParserRef);
        }
    }

    private void addChildsAsProperties(Model model, MainInfo main, ChildElementInfo childSpec, SchemaObject parentObjectSchema, boolean componentsContext, boolean listItemContext) {
        var childElementInfos = getChildElementDeclarationInfo(main, childSpec).getElementInfo().stream()
                // Top-level elements cannot be configurable as nested children
                .filter(candidateElementInfo -> !candidateElementInfo.getAnnotation().topLevel())
                .toList();

        // Generic list-item reference support:
        // If this list can contain at least one @MCElement(component=true) type,
        // allow "- $ref: ..." as an alternative list item shape.
        if (listItemContext && !componentsContext && childElementInfos.stream().anyMatch(candidateElementInfo -> candidateElementInfo.getAnnotation().component())) {
            parentObjectSchema.property(string($_REF).required(false));
        }

        for (ElementInfo childElementInfo : childElementInfos) {
            parentObjectSchema.property(getRef(model, childElementInfo))
                    .description(getDescriptionContent(childElementInfo))
                    .required(childSpec.isRequired());
        }
    }

    private static SchemaRef getRef(Model model, ElementInfo elementInfo) {
        return defsSchemaRef(elementInfo.getAnnotation().name(), elementInfo.getXSDTypeName(model));
    }

    private static ChildElementDeclarationInfo getChildElementDeclarationInfo(MainInfo main, ChildElementInfo childSpec) {
        return getChildElementDeclarations(main).get(childSpec.getTypeDeclaration());
    }

    private static Map<TypeElement, ChildElementDeclarationInfo> getChildElementDeclarations(MainInfo main) {
        return main.getChildElementDeclarations();
    }

    private SchemaArray createFromChild(ChildElementInfo childSpec, SchemaObject itemsObjectSchema) {
        return array(childSpec.getPropertyName())
                .items(itemsObjectSchema)
                .required(childSpec.isRequired())
                .description(getDescriptionContent(childSpec));
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

    private SchemaObject createComponentsMapParser(Model m, MainInfo main, ElementInfo elementInfo, String parserName) {
        SchemaObject parser = object(parserName)
                .additionalProperties(false) // only IDs via patternProperties
                .description(getDescriptionContent(elementInfo));
        parser.patternProperty(COMPONENT_ID_PATTERN, anyOf(getComponents(m, main)));
        return parser;
    }

    private static @NotNull ArrayList<AbstractSchema<?>> getComponents(Model m, MainInfo main) {
        var variants = new ArrayList<AbstractSchema<?>>();

        for (ElementInfo comp : main.getElements().values()) {
            if (!comp.getAnnotation().component()) continue;
            if (comp.getAnnotation().topLevel()) continue;

            String n = comp.getAnnotation().name();

            variants.add(object()
                    .title(n)
                    .additionalProperties(false)
                    .minProperties(1)
                    .property(defsSchemaRef(n, comp.getXSDTypeName(m))));
        }
        return variants;
    }

    private boolean hasComponentChild(ElementInfo parentElementInfo, MainInfo main) {
        for (ChildElementInfo childSpec : parentElementInfo.getChildElementSpecs()) {
            var childDeclaration = getChildElementDeclarationInfo(main, childSpec);
            if (childDeclaration == null) continue;

            if (childDeclaration.getElementInfo().stream().anyMatch(candidateElementInfo -> candidateElementInfo.getAnnotation().component()))
                return true;
        }
        return false;
    }

    private boolean shouldInlineListItems(MainInfo main, ChildElementInfo childSpec) {
        if (!childSpec.isList()) return false;
        if (childSpec.getAnnotation().allowForeign()) return false;

        var childDeclaration = getChildElementDeclarationInfo(main, childSpec);
        if (childDeclaration == null) return false;

        var candidateElementInfos = childDeclaration.getElementInfo().stream()
                .filter(candidateElementInfo -> !candidateElementInfo.getAnnotation().topLevel())
                .toList();

        // Only inline if there is exactly ONE possible list-item element type (no inheritance etc.)
        if (candidateElementInfos.size() != 1) return false;

        var itemElementInfo = candidateElementInfos.getFirst();

        if (itemElementInfo.getAnnotation().collapsed()) return false;
        if (itemElementInfo.getAnnotation().noEnvelope()) return false;

        return hasAnyConfigurableProperty(itemElementInfo, main);
    }

    private void attachArrayItems(ElementInfo parentElementInfo, AbstractSchema<?> parentSchema, ChildElementInfo childSpec, AbstractSchema<?> arrayItemsSchema) {
        // noEnvelope list: parent is an array already
        if (parentElementInfo.getAnnotation().noEnvelope() && parentSchema instanceof SchemaArray schemaArray) {
            schemaArray.items(arrayItemsSchema);
            return;
        }

        if (parentSchema instanceof SchemaObject schemaObject) {
            schemaObject.property(array(childSpec.getPropertyName())
                    .items(arrayItemsSchema)
                    .required(childSpec.isRequired())
                    .description(getDescriptionContent(childSpec)));
        }
    }

    private boolean hasAnyConfigurableProperty(ElementInfo elementInfo, MainInfo main) {
        return elementInfo.getAis().stream()
                .filter(attributeInfo -> !attributeInfo.excludedFromJsonSchema())
                .anyMatch(attributeInfo -> !"id".equals(attributeInfo.getXMLName()))
                || elementInfo.getTci() != null
                || !elementInfo.getChildElementSpecs().isEmpty()
                || elementInfo.getOai() != null
                || hasComponentChild(elementInfo, main);
    }

    private static String defsRefPath(String defName) {
        return JSON_SCHEMA_DEFS_PREFIX + defName;
    }

    private static SchemaRef defsSchemaRef(String propertyName, String defName) {
        return ref(propertyName).ref(defsRefPath(defName));
    }

    // For description. Probably we'll include that later. (Temporarily deactivated!)
    private String getDescriptionAsText(AbstractJavadocedInfo elementInfo) {
        return escapeJsonContent(getDescriptionContent(elementInfo).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim());
    }

    // For description. Probably we'll include that later. (Temporarily deactivated!)
    private String getDescriptionAsHtml(AbstractJavadocedInfo elementInfo) {
        return escapeJsonContent(getDescriptionContent(elementInfo).replaceAll("\\s+", " ").trim());
    }
}