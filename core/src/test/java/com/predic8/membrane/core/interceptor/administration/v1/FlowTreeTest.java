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
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.log.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static org.junit.jupiter.api.Assertions.*;

class FlowTreeTest {

    @Test
    void nestsRequestAndIfFlows() {
        final var log = new LogInterceptor();
        final var ifi = new IfInterceptor();
        ifi.setTest("method == 'POST'");
        ifi.setFlow(List.of(log));
        final var request = new RequestInterceptor();
        request.setFlow(List.of(ifi));

        final var tree = FlowTree.of(List.of(request));

        final var req = tree.getFirst();
        assertEquals("request", req.name());
        assertEquals("request", req.flow());
        final var ifNode = req.children().getFirst();
        assertEquals("if", ifNode.name());
        assertEquals("method == 'POST'", ifNode.condition());
        assertEquals("request", ifNode.flow());
        final var logNode = ifNode.children().getFirst();
        assertEquals("log", logNode.name());
        assertEquals("request", logNode.flow());
        assertEquals(FlowTree.DOC_BASE_URL + "log.html", logNode.docUrl());
    }

    @Test
    void appliedFlowNames() {
        assertEquals("both", FlowTree.appliedFlow(EnumSet.of(REQUEST, RESPONSE)));
        assertEquals("request", FlowTree.appliedFlow(EnumSet.of(REQUEST)));
        assertEquals("response", FlowTree.appliedFlow(EnumSet.of(RESPONSE)));
        assertEquals("abort", FlowTree.appliedFlow(EnumSet.of(ABORT)));
        assertNull(FlowTree.appliedFlow(EnumSet.noneOf(Interceptor.Flow.class)));
    }

    @Test
    void elementNameFallsBackToClassName() {
        assertEquals("String", FlowTree.elementName("x"));
    }
}
