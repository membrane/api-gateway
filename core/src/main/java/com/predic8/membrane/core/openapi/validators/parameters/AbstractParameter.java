/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.*;

public abstract class AbstractParameter {

    protected static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

//    protected List<String> values = new ArrayList<>();
    protected Map<String, List<String>> values;

    protected String type;
    protected boolean explode;
    protected Parameter parameter;
    protected OpenAPI api; // To grab $ref Schema types

    public static AbstractParameter instance(OpenAPI api, String type, Parameter parameter) {
        AbstractParameter paramParser = switch (type) {
            case ARRAY -> {
                if (parameter.getExplode())
                    yield new ExplodedArrayParameter();
                yield  new ArrayParameter();
            }
            case OBJECT -> {
                if (parameter.getExplode())
                    yield new ExplodedObjectParameter();
                yield new ObjectParameter();
            }
            default -> new ScalarParameter();
        };
        paramParser.type = type;
        paramParser.explode = parameter.getExplode();
        paramParser.parameter = parameter;
        paramParser.api = api;
        return paramParser;
    }

    public void setValues(Map<String, List<String>> values) {
        this.values = values;
    }

    protected List<String> getValuesForParameter() {
        return values.get(parameter.getName());
    }

    public abstract JsonNode getJson() throws JsonProcessingException;


}
