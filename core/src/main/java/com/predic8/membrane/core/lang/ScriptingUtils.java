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
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.util.FileUtil.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;

public class ScriptingUtils {

    private static final Logger log = LoggerFactory.getLogger(ScriptingUtils.class.getName());

    private static final ObjectMapper om = new ObjectMapper();

    public static HashMap<String, Object> createParameterBindings(Router router, Exchange exc, Flow flow, boolean includeJsonObject) {

        Message msg = exc.getMessage(flow);

        HashMap<String, Object> parameters = new HashMap<>();

        parameters.put("spring", router.getBeanFactory());

        // support both
        parameters.put("exc", exc);
        parameters.put("exchange", exc);

        parameters.put("flow", flow);

        if (flow == REQUEST) {
            try {
                parameters.put("params", getParams(router.getUriFactory(), exc, MERGE_USING_COMMA));
            } catch (Exception e) {
                log.info("Cannot parse query parameter from {}", exc.getRequest().getUri());
            }
        } else if (flow == RESPONSE) {
            Response response = exc.getResponse();
            parameters.put("response", response);
            parameters.put("statusCode", response.getStatusCode());
        }

        if (msg != null) {
            parameters.put("message", msg);
            parameters.put("header", msg.getHeader());
            parameters.put("headers", msg.getHeader());
            if (includeJsonObject) {
                try {
                    log.info("Parsing body as JSON for scripting plugins");
                    parameters.put("json",om.readValue(readInputStream(msg.getBodyAsStream()),Map.class));  // @Todo not with Javascript
                } catch (Exception e) {
                    log.warn("Can't parse body as JSON", e);
                }
            }
        }

        parameters.put("property", exc.getProperties());
        parameters.put("properties", exc.getProperties()); // properties does not work in Groovy scripts!
        parameters.put("props", exc.getProperties());
        return parameters;
    }
}
