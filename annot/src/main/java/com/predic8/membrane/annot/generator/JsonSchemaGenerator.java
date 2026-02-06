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

    // TODO keep this pattern or allow *?
    public static final String COMPONENT_ID_PATTERN = "^[A-Za-z_][A-Za-z0-9_-]*$";

    private final Map<String, Boolean> componentAdded = new HashMap<>();

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
        componentAdded.clear();

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
            String refName = "#/$defs/" + e.getXSDTypeName(m);
            schema.property(ref(name).ref(refName));
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

    private AbstractSchema<?> createParser(Model m, MainInfo main, ElementInfo elementInfo) {
        String parserName = elementInfo.getXSDTypeName(m);

        if (isComponentsMap(elementInfo)) {
            return createComponentsMapParser(m, main, elementInfo, parserName);
        }

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

        // enforce the inline form for collapsed elements
        if (elementInfo.getAnnotation().collapsed()) {
            if (elementInfo.getAnnotation().noEnvelope()) {
                throw new ProcessingException("@MCElement(collapsed=true) is not compatible with noEnvelope=true.", elementInfo.getElement());
            }
            return createCollapsedInlineParser(elementInfo, parserName);
        }

        SchemaObject parser = getParserSchemaObject(elementInfo, parserName);

        collectProperties(m, main, elementInfo, parser);

        // Allow object-level component reference if any setter expects a component.
        if (hasComponentChild(elementInfo, main) && !parser.hasProperty("$ref")) {
            parser.property(string("$ref")
                    .description("JSON Pointer to a component.")
                    .required(false));
        }

        return parser;
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
                .additionalProperties( elementInfo.isString())
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

            if (!cei.isList()) {
                addChildsAsProperties(m, main, cei, (SchemaObject) so, isComponentsList(i, cei), false);
                continue;
            }

            if (shouldGenerateFlowParserType(cei)) {
                processList(i, so, cei, getSchemaObjects(m, main, cei));
                continue;
            }

            if (shouldInlineListItems(main, cei)) {
                processInlineList(m, main, i, so, cei);
                continue;
            }

            AbstractSchema<?> parent2 = processList(i, so, cei, null);
            addChildsAsProperties(m, main, cei, (SchemaObject) parent2, isComponentsList(i, cei), true);
        }
    }

    private void processInlineList(Model m, MainInfo main, ElementInfo i, AbstractSchema<?> so, ChildElementInfo cei) {
        var decl = getChildElementDeclarationInfo(main, cei);

        ElementInfo itemEi = decl.getElementInfo().stream()
                .filter(ei -> !ei.getAnnotation().topLevel())
                .findFirst()
                .orElseThrow(); // should never happen due to shouldInlineListItems

        AbstractSchema<?> itemsSchema = ref(itemEi.getAnnotation().name()).ref("#/$defs/" + itemEi.getXSDTypeName(m));

        // keep "- $ref: ..." alternative for component items
        if (!isComponentsList(i, cei) && itemEi.getAnnotation().component()) {
            var variants = new ArrayList<AbstractSchema<?>>();
            variants.add(itemsSchema);
            variants.add(object()
                    .title("componentRef")
                    .additionalProperties(false)
                    .property(string("$ref").required(false)));
            itemsSchema = anyOf(variants);
        }

        attachArrayItems(i, so, cei, itemsSchema);
    }

    private static @NotNull ArrayList<AbstractSchema<?>> getSchemaObjects(Model m, MainInfo main, ChildElementInfo cei) {
        var sos = new ArrayList<AbstractSchema<?>>();

        for (ElementInfo ei : main.getChildElementDeclarations().get(cei.getTypeDeclaration()).getElementInfo()) {
            if (ei.getAnnotation().excludeFromFlow())
                continue;

            sos.add(object()
                    .title(ei.getAnnotation().name())
                    .additionalProperties(false)
                    .property(ref(ei.getAnnotation().name())
                            .ref("#/$defs/" + ei.getXSDTypeName(m))));
        }
        // Allow referencing a component instance directly on list-item level:
        // flow:
        //   - $ref: ...
        sos.add(object()
                .title("componentRef")
                .additionalProperties(false)
                .property( string("$ref")));
        return sos;
    }

    private boolean isComponentsList(ElementInfo parent, ChildElementInfo cei) {
        return COMPONENTS.equals(parent.getAnnotation().name())
               && parent.getAnnotation().noEnvelope()
               && COMPONENTS.equals(cei.getPropertyName());
    }

    private boolean shouldGenerateFlowParserType(ChildElementInfo cei) {
        return "flow".equals(cei.getPropertyName()) && !isFlowFromWebSocket(cei);
    }

    boolean isFlowFromWebSocket(ChildElementInfo cei) {
        // String had to be used cause for class we need dependency to core that is otherwise not needed.
        return "com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface".equals(cei.getTypeDeclaration().getQualifiedName().toString());
    }

    private AbstractSchema<?> processList(ElementInfo i, AbstractSchema<?> so, ChildElementInfo cei, ArrayList<AbstractSchema<?>> sos) {
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

    private void addFlowParserRef(AbstractSchema<?> so, List<AbstractSchema<?>> sos) {
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
        var eis = getChildElementDeclarationInfo(main, cei).getElementInfo().stream()
                // Top-level elements cannot be configurable as nested children
                .filter(ei -> !ei.getAnnotation().topLevel())
                .toList();

        // Generic list-item reference support:
        // If this list can contain at least one @MCElement(component=true) type,
        // allow "- $ref: ..." as an alternative list item shape.
        if (listItemContext && !componentsContext && eis.stream().anyMatch(ei -> ei.getAnnotation().component())) {
            parent2.property(string("$ref").required(false));
        }

        for (ElementInfo ei : eis) {

            parent2.property(getRef(m, ei))
                    .description(getDescriptionContent(ei))
                    .required(cei.isRequired());
        }
    }

    private static SchemaRef getRef(Model m, ElementInfo ei) {
        return ref(ei.getAnnotation().name()).ref("#/$defs/" + ei.getXSDTypeName(m));
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
                    .property(ref(n)
                            .ref("#/$defs/" + comp.getXSDTypeName(m))));
        }
        return variants;
    }

    private boolean hasComponentChild(ElementInfo parent, MainInfo main) {
        for (ChildElementInfo cei : parent.getChildElementSpecs()) {
            var decl = getChildElementDeclarationInfo(main, cei);
            if (decl == null) continue;

            if (decl.getElementInfo().stream().anyMatch(ei -> ei.getAnnotation().component()))
                return true;
        }
        return false;
    }

    private boolean shouldInlineListItems(MainInfo main, ChildElementInfo cei) {
        if (!cei.isList()) return false;

        if (cei.getAnnotation().allowForeign()) return false;

        var cedi = getChildElementDeclarationInfo(main, cei);
        if (cedi == null) return false;

        // Only inline if there is exactly ONE possible list-item element type
        var eis = cedi.getElementInfo().stream().filter(ei -> !ei.getAnnotation().topLevel()).toList();
        if (eis.size() != 1) return false;

        // Only inline if the item has >1 explicit attributes (excluding 'id')
        // TODO keep this?
        // TODO could removing this lead to errors with @collapsed elements?
        return countRelevantAttributes(eis.getFirst()) > 1;
    }

    private int countRelevantAttributes(ElementInfo ei) {
        return (int) ei.getAis().stream()
                .filter(ai -> !ai.excludedFromJsonSchema())
                .filter(ai -> !"id".equals(ai.getXMLName()))
                .count();
    }

    private void attachArrayItems(ElementInfo parentEi, AbstractSchema<?> parentSchema, ChildElementInfo cei, AbstractSchema<?> itemsSchema) {
        // noEnvelope list: parent is an array already
        if (parentEi.getAnnotation().noEnvelope() && parentSchema instanceof SchemaArray sa) {
            sa.items(itemsSchema);
            return;
        }

        if (parentSchema instanceof SchemaObject so) {
            so.property(array(cei.getPropertyName())
                    .items(itemsSchema)
                    .required(cei.isRequired())
                    .description(getDescriptionContent(cei)));
        }
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