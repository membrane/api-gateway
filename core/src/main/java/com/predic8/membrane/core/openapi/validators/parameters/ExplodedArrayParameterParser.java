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

import java.util.stream.*;

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.NULL;

public class ExplodedArrayParameterParser extends AbstractArrayParameterParser {

    protected Stream<String> getItems() {
        var vs = getValuesForParameter();
        if (vs.isEmpty()) return Stream.empty();
        if (vs.size() == 1) {
            var v = vs.getFirst();
            if (NULL.equals(v)) return null;
            if (v.isEmpty()) return Stream.empty();
        }
        return vs.stream();
    }
}
