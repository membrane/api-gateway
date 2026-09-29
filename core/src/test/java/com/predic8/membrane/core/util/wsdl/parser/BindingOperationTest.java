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

import static org.junit.jupiter.api.Assertions.assertEquals;

class BindingOperationTest {

    @Test
    void soapActionIsEmptyWithoutSoapOperation() throws Exception {
        var bindingOperation = Definitions.parse(new ResolverMap(), "classpath:/ws/no-soap-operation.wsdl")
                .getBindings().getFirst().findBindingOperation("greet").orElseThrow();

        assertEquals("", bindingOperation.getSoapAction());
    }
}
