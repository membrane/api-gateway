/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.lang;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.lang.groovy.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.util.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.matchTemplate;
import static com.predic8.membrane.core.util.FileUtil.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static java.util.Collections.emptyMap;

public class ScriptingUtils {

    private static final Logger log = LoggerFactory.getLogger(ScriptingUtils.class.getName());

    private static final ObjectMapper om = new ObjectMapper();

    public static HashMap<String, Object> createParameterBindings(Router router, Exchange exc, Flow flow, boolean includeJsonObject) {

        Message msg = exc.getMessage(flow);

        HashMap<String, Object> params = new HashMap<>();

        params.put("spring", router.getBeanFactory());

        // support both
        params.put("exc", exc);
        params.put("exchange", exc);

        params.put("flow", flow);

        if (flow == REQUEST) {
            try {
                params.put("params", getParams(router.getUriFactory(), exc, MERGE_USING_COMMA));
            } catch (Exception e) {
                log.info("Cannot parse query parameter from {}", exc.getRequest().getUri());
            }
        } else if (flow == RESPONSE) {
            Response response = exc.getResponse();
            params.put("response", response);
            params.put("statusCode", response.getStatusCode());
        }

        if (msg != null) {
            params.put("message", msg);
            params.put("header", msg.getHeader());
            params.put("headers", msg.getHeader());
            if (includeJsonObject) {
                try {
                    log.info("Parsing body as JSON for scripting plugins");
                    params.put("json",om.readValue(readInputStream(msg.getBodyAsStream()),Map.class));
                } catch (Exception e) {
                    log.warn("Can't parse body as JSON", e);
                }
            }
        }

        params.put("property", exc.getProperties());
        params.put("properties", exc.getProperties()); // properties does not work in Groovy scripts!
        params.put("props", exc.getProperties());

        params.put("pathParam", new PathParametersMap(exc));

        return params;
    }

    public static Map<String, String> extractPathParameters(Exchange exchange) {
        if (!(exchange.getProxy() instanceof APIProxy ap))
            return emptyMap();

        try {
            return new HashMap<>(matchTemplate(ap.getPath().getValue(), exchange.getRequestURI())); // Make lazy!
        } catch (PathDoesNotMatchException ignore) {
            // Log does only show up if path parameters are used in an expression
            log.info("No path parameters extracted: uriTemplate {}, path {}", ap.getPath().getValue(), exchange.getRequestURI());
        }
        // Add map to avoid a second parsing
        return emptyMap();
    }
}
