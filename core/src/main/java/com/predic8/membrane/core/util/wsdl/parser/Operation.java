package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.wsdl.*;
import org.w3c.dom.*;

import javax.xml.namespace.*;
import java.util.*;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.*;
import static org.w3c.dom.Node.*;

public class Operation extends WSDLElement {

    private final List<Message> inputs;
    private final List<Message> outputs;
    private final List<Fault> faults;

    public enum Direction {
        INPUT, OUTPUT;

        public boolean matches(String s) {
            return name().equalsIgnoreCase(s);
        }
    }

    public Operation(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        inputs = getInputs(node);
        outputs = getOutputs(node);
        faults = getFaults(node);
    }

    public List<Message> getInputs() {
        return inputs;
    }

    public List<Message> getOutputs() {
        return outputs;
    }

    public List<Message> getMessagesByDirection(Direction direction) {
        if (direction == INPUT)
            return inputs;
        return outputs;
    }

    public List<Fault> getFaults() {
        return faults;
    }

    private List<Message> getInputs(Node node) {
        return getMessagesByDirection(node, INPUT);
    }

    private List<Message> getOutputs(Node node) {
        return getMessagesByDirection(node, OUTPUT);
    }

    private List<Fault> getFaults(Node node) {
        return new ArrayList<>();
    }

    private List<Message> getMessagesByDirection(Node node, Direction direction) {
        var result = new ArrayList<Message>();
        var children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == ELEMENT_NODE
                && direction.matches(child.getLocalName())
                && WSDL11_NS.equals(child.getNamespaceURI())) {

                Element io = (Element) child;
                String messageAttr = io.getAttribute("message");
                if (messageAttr.isEmpty()) {
                    continue;
                }

                QName messageQName = WSDLParserUtil.resolveQName(messageAttr, io);
                Message message = findMessage(messageQName, io.getOwnerDocument());
                if (message != null) {
                    result.add(message);
                }
            }
        }

        return result;
    }

    private Message findMessage(QName messageQName, Document document) {
        var definitions = document.getDocumentElement();
        var messages = definitions.getElementsByTagNameNS(WSDL11_NS, "message");

        for (int i = 0; i < messages.getLength(); i++) {
            Element message = (Element) messages.item(i);
            if (messageQName.getLocalPart().equals(message.getAttribute("name"))) {
                return new Message(ctx, message);
            }
        }

        return null;
    }
}
