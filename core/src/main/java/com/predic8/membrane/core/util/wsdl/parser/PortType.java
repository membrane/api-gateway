package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.annot.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.annot.Constants.WSDL11_NS;
import static org.w3c.dom.Node.*;

public class PortType extends WSDLElement {

    private List<Operation> operations;

    public PortType(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        operations = getOperations(node);
        ctx.getDefinitions().portTypes.add(this);
    }

    public List<Operation> getOperations() {
        return operations;
    }

    private List<Operation> getOperations(Node node) {
        var operations = new ArrayList<Operation>();
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == ELEMENT_NODE
                && "operation".equals(child.getLocalName())
                && WSDL11_NS.equals(child.getNamespaceURI())
            ) {
                operations.add(new Operation(ctx, child));
            }
        }

        return operations;
    }
}
