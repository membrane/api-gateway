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

import com.predic8.wsdl.Port;
import com.predic8.wsdl.Service;
import com.predic8.wsdl.WSDLParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebServiceOASWrapperIntegrationTest {

    private static WebServiceOASWrapper wrapper;
    private static Service service;

    @BeforeAll
    static void setup() {
        service = new ArrayList<>(new WSDLParser().parse(
                WebServiceOASWrapperIntegrationTest.class.getResourceAsStream("/validation/ArticleService.wsdl")
                //WebServiceOASWrapperIntegrationTest.class.getResourceAsStream("/blz-service-double-binding.wsdl")
        ).getServices()).getFirst();
        wrapper = new WebServiceOASWrapper(service);
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