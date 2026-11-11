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

package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.util.wsdl.parser.*;
import org.w3c.dom.*;

import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

public class Include extends AbstractIncludeImport {

    public Include(WSDLParserContext ctx, Node node) {
        super(ctx,node);
    }

    /**
     * An included schema without a {@code targetNamespace} takes on the namespace of the schema
     * including it — a "chameleon" include (XSD 1.0, 4.2.1). Its root gets that
     * {@code targetNamespace}, and, unless it binds a default namespace of its own, that namespace
     * as its default, so that its unprefixed references to its own components resolve into it.
     * The DOM is this include's own, freshly parsed, so rewriting it affects nothing else; a
     * document already visited is not parsed again, so only its first includer gives it a namespace.
     * Because the including schema has been adopted the same way before its includes are resolved,
     * the namespace is passed on through nested includes.
     */
    @Override
    protected Element adopt(Element schemaRoot) {
        final var namespace = ((Element) element.getParentNode()).getAttribute("targetNamespace");
        if (namespace.isEmpty() || !schemaRoot.getAttribute("targetNamespace").isEmpty()) return schemaRoot;

        schemaRoot.setAttribute("targetNamespace", namespace);
        final var defaultNamespace = schemaRoot.lookupNamespaceURI(null);
        if (defaultNamespace == null || defaultNamespace.isEmpty()) {
            schemaRoot.setAttributeNS(XMLNS_ATTRIBUTE_NS_URI, "xmlns", namespace);
        }
        return schemaRoot;
    }
}
