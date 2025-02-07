/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.integration.withinternet;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class OpenAPIRecordFactoryIntegrationTest {

    static OpenAPIRecordFactory factory;

    @BeforeAll
    static void setUp() {
        factory = new OpenAPIRecordFactory(new Router());
    }

    @Test
    void readAndParseOpenAPI31() {
        Collection<OpenAPISpec> specs = new ArrayList<>();
        specs.add(new OpenAPISpec() {{
            setLocation("https://api.predic8.de/api-docs/fruit-shop-api-v2-2-0");
        }});

        Map<String, OpenAPIRecord> recs = factory.create(specs);
        assertEquals(1, recs.size());

        recs.forEach((key, value) -> {
            assertTrue(value.getApi().getInfo().getTitle().startsWith("Fruit Shop"));
            assertTrue(key.startsWith("fruit-shop"));
        });
    }
}