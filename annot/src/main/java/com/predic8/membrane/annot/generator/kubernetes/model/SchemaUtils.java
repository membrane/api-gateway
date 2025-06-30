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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SchemaUtils {

    public static String entryToJson(Map.Entry<String, Object> entry) {
        if (entry.getValue() instanceof SchemaObject)
            return entry.getValue().toString();
        if (entry.getValue() instanceof String) {
            return "\"" + entry.getKey() + "\": \"" + entry.getValue() + "\"";
        }
        return "\"" + entry.getKey() + "\": " + entry.getValue();
    }

    public static String printRequired(List<SchemaObject> properties) {
        String req = properties.stream()
                .filter(SchemaObject::isRequired)
                .map(so -> "\"" + so.getName() + "\"")
                .collect(Collectors.joining(","));

        if (req.isEmpty())
            return "";

        return ",\"required\":[" + req + "]";
    }

}
