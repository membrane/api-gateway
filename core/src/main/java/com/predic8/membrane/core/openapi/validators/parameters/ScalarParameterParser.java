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
import com.predic8.membrane.core.util.*;

public class ScalarParameterParser extends AbstractParameterParser {

    @Override
    public JsonNode getJson() throws JsonProcessingException {

        System.out.println("values = " + values);

//        // TODO What if foo=1&foo=2 ?
//        List<String> values = getValuesForParameter();
//        if (values == null) {
//
//        }
        return JsonUtil.scalarAsJson(getValuesForParameter().getFirst());

//        if (values.isEmpty()) {
//            // Interpret absence of a concrete value as JSON null; validator will enforce required/nullable.
//            return FACTORY.nullNode();
//        }
//        return scalarAsJson(values.getFirst());
    }
}
