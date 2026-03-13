package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static org.w3c.dom.Node.*;

public class Schema extends WSDLElement {

    private WSDLParserContext ctx;

    private String targetNamespace;
    private Element schema;
    private List<SchemaElement> schemaElements;
    private List<Import> imports;
    private List<Include> includes;

    public Schema(WSDLParserContext ctx, Node node) {
        super(node);
        this.ctx = ctx;
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
        return (tns == null || tns.isEmpty()) ? null : tns;
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
