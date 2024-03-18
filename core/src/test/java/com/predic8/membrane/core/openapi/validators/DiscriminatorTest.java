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

import com.predic8.membrane.core.openapi.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static org.junit.jupiter.api.Assertions.*;


@SuppressWarnings("OptionalGetWithoutIsPresent")
public class DiscriminatorTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/discriminator.yml";
    }

    @Test
    public void discriminatorLengthWrong() throws RuntimeException{

        Map<String,Object> publicTransport = new HashMap<>();
        publicTransport.put("kind","Train");
        publicTransport.put("name","Bimmelbahn");
        publicTransport.put("seats",254);
        publicTransport.put("length","5cm");

        ValidationErrors errors = validator.validate(Request.post().path("/public-transports").body(mapToJson(publicTransport)));
        assertEquals(2,errors.size());

        ValidationError numberError = errors.stream().filter(e -> e.getMessage().contains("number")).findFirst().get();
        assertEquals("/length", numberError.getContext().getJSONpointer());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findFirst().get();
        assertTrue(allOf.getMessage().contains("subschemas"));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void discriminatorNoWheels() {

        Map<String,Object> publicTransport = new HashMap<>();
        publicTransport.put("kind","Bus");
        publicTransport.put("name","Postbus");
        publicTransport.put("seats",45);

        ValidationErrors errors = validator.validate(Request.post().path("/public-transports").body(mapToJson(publicTransport)));
        assertEquals(2,errors.size());


        ValidationError requiredError = errors.stream().filter(e -> e.getMessage().toLowerCase().contains("required")).findAny().get();
        assertEquals("/wheels", requiredError.getContext().getJSONpointer());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findAny().get();
        assertTrue(allOf.getMessage().contains("subschemas"));
    }
}