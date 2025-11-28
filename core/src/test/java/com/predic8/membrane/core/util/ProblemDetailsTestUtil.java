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

package com.predic8.membrane.core.util;

import tools.jackson.databind.core.*;
import tools.jackson.databind.core.type.*;
import tools.jackson.databind.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.http.*;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_PROBLEM_JSON;

public class ProblemDetailsTestUtil {

    private final static ObjectMapper om = new ObjectMapper();
    public static ProblemDetails parse(Response r) throws JsonProcessingException {

        if (r.getHeader().getContentType() == null)
            throw new RuntimeException("No Content-Type in message with ProblemDetails!");

        if (!r.getHeader().getContentType().equals(APPLICATION_PROBLEM_JSON))
            throw new RuntimeException("Content-Type ist %s but should be %s.".formatted(r.getHeader().getContentType(), APPLICATION_PROBLEM_JSON));

        Map<String, Object> m = om.readValue(r.getBodyAsStringDecoded(), new TypeReference<>() {
        });

        ProblemDetails pd = new ProblemDetails() {{
            type((String) m.get(TYPE)); // type is protected
        }};


        pd.title ((String) m.get(TITLE))
                .detail ((String) m.get(DETAIL))
                .status(r.getStatusCode());

        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (pd.isReservedProblemDetailsField(e.getKey()))
                continue;
            pd.internal(e.getKey(), e.getValue());
        }
        return pd;
    }
}
