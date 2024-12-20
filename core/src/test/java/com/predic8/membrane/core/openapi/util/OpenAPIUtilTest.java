/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.openapi.util;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.util.TestUtils.getYAMLResource;
import static com.predic8.membrane.core.util.FileUtil.readInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenAPIUtilTest {

    @Test
    void getIdFromAPITest() {
        assertEquals("customers-api-v1-0", getIdFromAPI(getApi("/openapi/specs/customers.yml")) );
        assertEquals("servers-3-api-v1-0", getIdFromAPI(getApi("/openapi/specs/info-3-servers.yml")) );
    }

    @Test
    void idFromXMembraneId() {
        assertEquals("extension-sample-v1-4", getIdFromAPI(getApi("/openapi/specs/x-membrane.yaml")) );
    }

    private OpenAPI getApi(String pfad) {
        return new OpenAPIParser().readContents(readInputStream(getResourceAsStream(this,pfad)), null, null).getOpenAPI();
    }

    @Test
    void isOpenAPI3Test() throws IOException {
        assertTrue(isOpenAPI3(getYAMLResource(this,"/openapi/specs/array.yml")));
    }

    @Test
    void isSwagger2Test() throws IOException {
        assertTrue(isSwagger2(getYAMLResource(this,"/openapi/specs/fruitshop-swagger-2.0.json")));
    }
}