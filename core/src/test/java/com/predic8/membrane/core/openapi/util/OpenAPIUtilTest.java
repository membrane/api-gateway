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

import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAPIUtilTest {

    @Test
    void getIdFromAPITest() {
        assertEquals("customers-api-v1-0", getIdFromAPI(getApi(this,"/openapi/specs/customers.yml")) );
        assertEquals("servers-3-api-v1-0", getIdFromAPI(getApi(this,"/openapi/specs/info-3-servers.yml")) );
    }

    @Test
    void idFromXMembraneId() {
        assertEquals("extension-sample-v1-4", getIdFromAPI(getApi(this,"/openapi/specs/x-membrane.yaml")) );
    }

    @Test
    void isOpenAPI3Test() throws IOException {
        assertTrue(isOpenAPI3(getYAMLResource(this,"/openapi/specs/array.yml")));
    }

    @Test
    void isSwagger2Test() throws IOException {
        assertTrue(isSwagger2(getYAMLResource(this,"/openapi/specs/fruitshop-swagger-2.0.json")));
    }

    @Test
    void getOpenAPIVersionTest() throws IOException {
        assertEquals("3.0.2", getOpenAPIVersion(getYAMLResource(this,"/openapi/specs/array.yml")));
        assertEquals("2.0", getOpenAPIVersion(getYAMLResource(this,"/openapi/specs/fruitshop-swagger-2.0.json")));
    }

    @Test
    void validOpenAPIMisplacedError() {
        assertTrue(isOpenAPIMisplacedError("Invalid content was found starting with element '{\"http://membrane-soa.org/proxies/1/\":openapi}'."));
    }

    @Test
    void invalidOpenAPIMisplacedError() {
        assertFalse(isOpenAPIMisplacedError("Invalid content was found starting with element '{\"http://membrane-soa.org/proxies/1/\":api}'"));
    }

    @Test
    void getPath() {
        assertEquals("Single paths", OpenAPIUtil.getPath(OpenAPITestUtils.getApi(this,"/openapi/specs/customers.yml"), "/customers/{cid}").getDescription());
    }

    @Test
    void getOperation() {

    }
}