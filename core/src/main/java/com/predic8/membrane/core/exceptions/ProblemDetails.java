/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exceptions;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

public class ProblemDetails {

    private final static ObjectMapper om = new ObjectMapper();

    public static byte[] createProblemDetails(String type, String title, String details) throws JsonProcessingException {
        ObjectNode root = om.createObjectNode();
        root.put("type", type);
        root.put("title", title);
        if (details != null)
            root.put("details", details);
        return om.writeValueAsBytes(root);
    }
}
