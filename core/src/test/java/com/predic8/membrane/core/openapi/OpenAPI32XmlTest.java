/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.validators.ValidationErrors;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.parseOpenAPI32;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates XML request bodies described with the OpenAPI 3.2 {@code xml.nodeType} keyword
 * ({@code attribute}, {@code element} for a wrapped array, {@code text} for element text content).
 */
class OpenAPI32XmlTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OpenAPIValidator(new URIFactory(),
                new OpenAPIRecord(parseOpenAPI32(this, "/openapi/specs/oas32/xml-nodetype.yaml"), new OpenAPISpec()));
    }

    private ValidationErrors validate(String xml) throws Exception {
        return validator.validate(Request.post().path("/orders").xml(xml));
    }

    @Test
    void validOrder() throws Exception {
        ValidationErrors errors = validate("""
                <order id="A1">
                  <price currency="USD">42.5</price>
                  <items>
                    <item>book</item>
                    <item>pen</item>
                  </items>
                </order>""");
        assertEquals(0, errors.size(), errors.toString());
    }

    @Test
    void missingAttributeIsInvalid() throws Exception {
        // id has nodeType: attribute and is required; here the id attribute is absent.
        ValidationErrors errors = validate("""
                <order>
                  <price currency="USD">42.5</price>
                  <items>
                    <item>book</item>
                  </items>
                </order>""");
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).getMessage().contains("id"));
    }

    @Test
    void textContentWrongTypeIsInvalid() throws Exception {
        // value has nodeType: text and type number; "abc" is not a number.
        ValidationErrors errors = validate("""
                <order id="A1">
                  <price currency="USD">abc</price>
                  <items>
                    <item>book</item>
                  </items>
                </order>""");
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("/price/value", errors.get(0).getContext().getJSONpointer());
    }
}
