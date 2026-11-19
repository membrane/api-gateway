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

package com.predic8.membrane.core.interceptor.soap2json;

import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SelectedPortTest {

    @Test
    void aWsdlWithoutServiceAndSeveralPortTypesIsRejectedWithoutNamingAnInterceptor() throws Exception {
        final var definitions = Definitions.parse(new ResolverMap(), "classpath:/ws/no-service-two-port-types.wsdl");

        final var e = assertThrows(ConfigurationException.class, () -> SelectedPort.select(definitions, null, null));
        // soap2Json, json2Soap and wsdl2openapi all select their port here.
        assertFalse(e.getMessage().contains("wsdl2openapi"), e.getMessage());
        assertTrue(e.getMessage().contains("single port type"), e.getMessage());
    }
}
