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

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.util.*;
import io.swagger.parser.*;
import io.swagger.v3.oas.models.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.HashMap;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.parseOpenAPI;
import static com.predic8.membrane.core.util.FileUtil.readInputStream;

public abstract class AbstractValidatorTest {

    protected final static ObjectMapper om = new ObjectMapper();

    protected OpenAPIValidator validator;

    @BeforeEach
    public void setUp() throws URISyntaxException {
        validator = new OpenAPIValidator(new URIFactory(), getResourceAsStream(getOpenAPIFileName()));
        validator.getApi().setExtensions(new HashMap<>() {{
            put(X_MEMBRANE_VALIDATION, new HashMap<>() {{
                put(SECURITY, true);
                put(REQUESTS, true);
            }});
        }});
    }

    protected abstract String getOpenAPIFileName();

    protected InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }
}