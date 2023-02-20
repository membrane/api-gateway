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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.http.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;

public class ProblemDetails {

    public static final String DESCRIPTION = "description";

    private final static ObjectWriter om = new ObjectMapper().writerWithDefaultPrettyPrinter();


    public static Response createProblemDetails(int statusCode, String type, String title) {
        return createProblemDetails(statusCode,type,title,null);
    }

    public static Response createProblemDetails(int statusCode, String type, String title, Map<String,Object> details) {
        Map<String,Object> root = new HashMap<>();
        root.put("type","http://membrane-api.io/error" + type);
        root.put("title",title);

        if (details != null) {
            root.put("details", details);
        }

        return createMessage(statusCode, type, title, details, root);
    }

    private static Response createMessage(int statusCode, String type, String title, Map<String, Object> details, Map<String, Object> root) {
        Response.ResponseBuilder builder = Response.statusCode(statusCode);
        try {
            builder.body(om.writeValueAsBytes(root));
            builder.contentType(APPLICATION_PROBLEM_JSON);
        }
        catch (Exception e) {
            builder.body("Type: %s Title: %s Details: %s".formatted(type, title, details).getBytes());
            builder.contentType(TEXT_PLAIN);
        }
        return builder.build();
    }
}