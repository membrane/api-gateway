package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.annot.Constants.*;
import static org.w3c.dom.Node.*;

public class Schema extends WSDLElement {

    private final String targetNamespace;

    /**
     * DOM Element of the schema as read from the WSDL.
     */
    private final Element schema;

    private final List<SchemaElement> schemaElements;
    private final List<Import> imports;
    private final List<Include> includes;

    public Schema(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        this.targetNamespace = getTargetNamespace(node);
        this.schema = (Element) node;
        this.schemaElements = getSchemaElements(schema);
        this.imports = getImports(node);
        this.includes = getIncludes(node);
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public Element getSchemaElement() {
        return schema;
    }

    public List<SchemaElement> getSchemaElements() {
        return schemaElements;
    }

    public List<Import> getImports() {
        return imports;
    }

    public List<Include> getIncludes() {
        return includes;
    }

    private String getTargetNamespace(Node node) {
        if (!(node instanceof Element schema)) {
            return null;
        }

        String tns = schema.getAttribute("targetNamespace");
        return (tns.isEmpty()) ? null : tns;
    }

    private List<Include> getIncludes(Node node) {
        var result = new ArrayList<Include>();
        var children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);

            if (child.getNodeType() == ELEMENT_NODE
                && "include".equals(child.getLocalName())
                && XSD_NS.equals(child.getNamespaceURI())) {
                result.add(new Include(ctx, child, this));
            }
        }

        return result;
    }

    private List<Import> getImports(Node node) {
        var result = new ArrayList<Import>();
        var children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);

            if (child.getNodeType() == ELEMENT_NODE
                && "import".equals(child.getLocalName())
                && XSD_NS.equals(child.getNamespaceURI())) {
                result.add(new Import(ctx, child));
            }
        }

        return result;
    }

    private List<SchemaElement> getSchemaElements(Element schema) {
        var result = new ArrayList<SchemaElement>();
        var children = schema.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);

            if (child.getNodeType() == ELEMENT_NODE
                && "element".equals(child.getLocalName())
                && XSD_NS.equals(child.getNamespaceURI())) {
                result.add(new SchemaElement(ctx, child));
            }
        }
        return result;
    }

}
