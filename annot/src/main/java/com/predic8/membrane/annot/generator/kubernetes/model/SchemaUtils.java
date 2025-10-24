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

package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.*;

import java.util.*;

import static java.util.stream.Collectors.*;

public class SchemaUtils {

    private final static ObjectMapper om = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(RefObj.class, new RefObjSerializer());
        om.registerModule(module);
    }

    public static String entryToJson(Map.Entry<String, Object> entry) {
        if (entry.getValue() instanceof SchemaObject)
            return entry.getValue().toString();

        try {
            if ("enum".equals(entry.getKey()) || entry.getValue() instanceof Boolean || entry.getValue() instanceof String) {
                return "\"" + entry.getKey() + "\":" + om.writeValueAsString(entry.getValue());
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // List needs to serialized with toString may migrate later to proper serialization
        return "\"" + entry.getKey() + "\": " + entry.getValue();
    }

    public static String printRequired(List<AbstractSchema> properties) {
        String req = properties.stream()
                .filter(AbstractSchema::isRequired)
                .map(so -> "\"" + so.getName() + "\"")
                .collect(joining(","));

        if (req.isEmpty())
            return "";

        return ",\"required\":[" + req + "]";
    }
}