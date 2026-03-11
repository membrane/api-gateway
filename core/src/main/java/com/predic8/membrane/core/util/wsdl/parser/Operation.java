/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

import java.util.*;

public class Operation extends WSDLElement {

    public enum Direction {
        INPUT, OUTPUT, FAULT;

        public boolean matches(String s) {
            return name().equalsIgnoreCase(s);
        }
    }

    public Operation(WSDLParserContext ctx, Node node) {
        super(ctx, node);
    }

    public List<Input> getInputs() {
        return instantiateChildren("input", Input.class);
    }

    public List<Output> getOutputs() {
        return instantiateChildren("output", Output.class);
    }

    public List<Fault> getFaults() {
        return instantiateChildren("fault", Fault.class);
    }

    public List<Message> getMessagesByDirection(Direction direction) {
        return switch (direction) {
            case INPUT -> getInputs().stream().map(Input::getMessage).toList();
            case OUTPUT -> getOutputs().stream().map(Output::getMessage).toList();
            case FAULT -> getFaults().stream().map(Fault::getMessage).toList();
        };
    }

    public static abstract class OperationMessage extends WSDLElement {
        public OperationMessage(WSDLParserContext ctx, Node node) {
            super(ctx, node);
        }

        public Message getMessage() {
            return ctx.definitions().findMessage(WSDLParserUtil.getLocalName(getAttribute("message")))
                    .orElseThrow(() -> new WSDLParserException("Message not found: " + getAttribute("message")));
        }
    }

    public static class Input extends OperationMessage {
        public Input(WSDLParserContext ctx, Node node) {
            super(ctx, node);
        }
    }

    public static class Output extends OperationMessage {
        public Output(WSDLParserContext ctx, Node node) {
            super(ctx, node);
        }
    }

    public static class Fault extends OperationMessage {
        public Fault(WSDLParserContext ctx, Node node) {
            super(ctx, node);
        }
    }
}
