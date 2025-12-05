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

package com.predic8.membrane.core.interceptor.schemavalidation.json;

import com.networknt.schema.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;

import static com.networknt.schema.SpecificationVersion.*;

public class JSONSchemaVersionParser {

    public static SpecificationVersion parse(String version) {
        return aliasToSpecId(version);
    }

    static @NotNull SpecificationVersion aliasToSpecId(String alias) {
        if (alias == null)
            throw new ConfigurationException("Unknown JSON Schema version: " + alias);
        return switch (alias) {
            case "04","draft-04" -> DRAFT_4;
            case "06","draft-06" -> DRAFT_6;
            case "07","draft-07" -> DRAFT_7;
            case "2019-09" -> DRAFT_2019_09;
            case "2020-12" -> DRAFT_2020_12;
            default -> throw new ConfigurationException("Unknown JSON Schema version: " + alias);
        };
    }
}
