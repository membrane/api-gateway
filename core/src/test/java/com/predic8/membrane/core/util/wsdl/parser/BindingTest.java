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

import com.predic8.membrane.core.resolver.ResolverMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BindingTest {

    @Test
    void findBindingOperationByName() throws Exception {
        var binding = binding("OrderSoapBinding");

        assertEquals("http://example.com/navigation/order",
                binding.findBindingOperation("order").orElseThrow().getSoapAction());
        assertTrue(binding.findBindingOperation("notify").isEmpty(), "the binding does not cover notify");
        assertTrue(binding.findBindingOperation(null).isEmpty());
    }

    @Test
    void soapAndNonSoapBindings() throws Exception {
        assertTrue(binding("OrderSoapBinding").isSoap());
        assertFalse(binding("OrderHttpBinding").isSoap());
    }

    private static Binding binding(String name) throws Exception {
        return Definitions.parse(new ResolverMap(), "classpath:/ws/navigation.wsdl").getBindings().stream()
                .filter(b -> name.equals(b.getName())).findFirst().orElseThrow();
    }
}
