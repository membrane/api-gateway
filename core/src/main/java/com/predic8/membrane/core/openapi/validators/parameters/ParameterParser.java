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

import com.fasterxml.jackson.databind.*;

import java.util.*;

public interface ParameterParser {
    
    /**
     * Build a JSON representation of the bound parameter values.
     * Implementations must be side‑effect free and thread‑safe.
     * This method is responsible to URL decode the single parameter values.
     */
    JsonNode getJson() throws ParameterParsingException;

    /**
     * Provide the raw query parameter values. Implementations must not retain
     * a mutable reference; copy defensively if needed.
     */
    void setValues(Map<String, List<String>> values);
}
