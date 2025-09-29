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

import java.util.*;
import java.util.stream.*;

public class ArrayParameter extends AbstractParameter {

    @Override
    public JsonNode getJson() throws JsonProcessingException {
        ArrayNode an = factory.arrayNode();
        Stream<String> items = getItems();
        // e.g. foo=null
        if (items == null) {
            return factory.missingNode();
        }
        items.forEach(s -> an.add(asJson(s)));
        return an;
    }

    private Stream<String> getItems() {
        if (explode) {
            return values.stream();
        }
        String[] items = values.getFirst().split(",");
        if (items.length == 0) {
            return Stream.empty();
        }
        if (items.length == 1) {
            if (items[0].equals("null")) {
                return null;
            }
            // foo= => foo: "" => Let assume an empty parameter is an empty array
            if ("".equals(items[0])) {
                return Stream.empty();
            }
        }
        return Arrays.stream(items);
    }

}
