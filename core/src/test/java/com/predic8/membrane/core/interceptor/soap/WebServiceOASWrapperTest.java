/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.test.*;
import com.predic8.schema.*;
import com.predic8.wsdl.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WebServiceOASWrapperIntegrationTest {

    private static WebServiceOASWrapper wrapper;
    private static Service service;

    @BeforeAll
    static void setup() {
        service = new ArrayList<>(new WSDLParser().parse(TestUtil.getPathFromResource("/validation/ArticleService.wsdl")
//                WebServiceOASWrapperIntegrationTest.class.getResourceAsStream()
                //WebServiceOASWrapperIntegrationTest.class.getResourceAsStream("/blz-service-double-binding.wsdl")
        ).getServices()).getFirst();
        wrapper = new WebServiceOASWrapper(service);
    }

    @Test
    void foo() {
        var qnt =service.getPorts().getFirst().getBinding().getPortType().getOperations().getFirst().getInput().getMessage().getParts().getFirst().getElement().getType();
        System.out.println(qnt);

        var def = new WSDLParser().parse(TestUtil.getPathFromResource("/validation/ArticleService.wsdl"));
        ComplexType ct = (ComplexType) def.getSchemaType(qnt);
        System.out.println(ct);

        var m = ct.getModel();
        if (m instanceof Sequence s) {
            s.getElements().forEach(e -> {
                System.out.println(e);
                var tqn = e.getType();
                var type = def.getSchemaType(tqn);
                System.out.println("type = " + type);
            });
        }

        var t = ct.getRequestTemplate();
        System.out.println(t);

        var x = new WSDLParser().parse(TestUtil.getPathFromResource("/validation/ArticleService.wsdl")
//                WebServiceOASWrapperIntegrationTest.class.getResourceAsStream()
                //WebServiceOASWrapperIntegrationTest.class.getResourceAsStream("/blz-service-double-binding.wsdl")
        );

        //var e = x.getElement(new QName(en.getNamespaceURI(), en.getLocalPart()) );

    }

    @Test
    void getAggregatedPorts() {
        Map<Port, @NotNull List<String>> aggregatedPorts = WebServiceOASWrapper.getAggregatedPorts(service);
        assertEquals(2, aggregatedPorts.size());
    }

    // What about multiple ports having the same URL? How to handle? Is aggregating those as well fine?
    @Test
    void testGetApiRecords() {
        var records = wrapper.getApiRecords().toList();
        assertEquals(2, records.size());
        assertEquals(1, records.getFirst().getValue().getApi().getComponents().getRequestBodies().size());
    }
}