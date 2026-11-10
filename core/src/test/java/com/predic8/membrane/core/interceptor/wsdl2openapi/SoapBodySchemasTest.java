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
package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SoapBodySchemasTest {

    private static final String SHOP_NS = "https://example.com/shop";

    private static SoapBodySchemas schemasOf(String wsdl) throws Exception {
        final var definitions = Definitions.parse(new ResolverMap(), wsdl);
        return SoapBodySchemas.of(SelectedPort.select(definitions, null, null), new XsdToSchema(definitions, Set.of()));
    }

    @Test
    void inputAndOutputElementsOfEveryOperationAreIndexed() throws Exception {
        final var schemas = schemasOf("classpath:/ws/two-operations-with-faults.wsdl");

        assertEquals(Set.of(new QName(SHOP_NS, "getArticle"), new QName(SHOP_NS, "getArticleResponse"),
                        new QName(SHOP_NS, "placeOrder"), new QName(SHOP_NS, "placeOrderResponse")),
                schemas.bodySchemas().keySet());
        assertNotNull(schemas.forElement(new QName(SHOP_NS, "getArticleResponse")).getProperties().get("price"));
    }

    @Test
    void theFaultDetailSchemaCoversTheFaultsOfAllOperations() throws Exception {
        // A Fault does not name the operation that failed, so every declared fault must be typed.
        assertEquals(Set.of("articleNotFound", "outOfStock"),
                schemasOf("classpath:/ws/two-operations-with-faults.wsdl").faultDetailSchema().getProperties().keySet());
    }

    @Test
    void anElementNoMessageDeclaresIsRejected() throws Exception {
        final var schemas = schemasOf("classpath:/ws/two-operations-with-faults.wsdl");
        final var element = new QName("https://example.com/other", "getArticle");

        final var e = assertThrows(UndeclaredBodyElementException.class, () -> schemas.forElement(element));
        assertEquals(element, e.getElement());
    }

    @Test
    void anInputElementNamesItsOperation() throws Exception {
        final var schemas = schemasOf("classpath:/ws/two-operations-with-faults.wsdl");

        assertEquals(Optional.of("placeOrder"), schemas.inputOperation(new QName(SHOP_NS, "placeOrder")));
        assertEquals(Optional.empty(), schemas.inputOperation(new QName(SHOP_NS, "placeOrderResponse")),
                "an output element names no operation of a request");
    }

    @Test
    void anInputElementOfSeveralOperationsNamesNone() throws Exception {
        final var schemas = schemasOf("classpath:/ws/shared-input-element.wsdl");
        final var ns = "https://example.com/shared";

        assertEquals(Optional.empty(), schemas.inputOperation(new QName(ns, "query")));
        assertEquals(Optional.of("ping"), schemas.inputOperation(new QName(ns, "ping")));
        assertNotNull(schemas.forElement(new QName(ns, "query")), "the element is still converted");
    }

    @Test
    void aPortWithoutDocumentLiteralMessagesIsAConfigurationError() {
        // RPC-style parts name types, not elements: there is nothing to look a Body element up by.
        assertThrows(ConfigurationException.class, () -> schemasOf("classpath:/ws/rpc-with-fault.wsdl"));
    }
}
