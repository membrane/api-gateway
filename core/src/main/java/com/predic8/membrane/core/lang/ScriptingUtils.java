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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.util.FileUtil.readInputStream;

public class ScriptingUtils {

    private static final Logger log = LoggerFactory.getLogger(ScriptingUtils.class.getName());

    private static final ObjectMapper om = new ObjectMapper();

    public static HashMap<String, Object> createParameterBindings(Exchange exc, Message msg, Interceptor.Flow flow, boolean includeJsonObject) {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("exc", exc);
        parameters.put("flow", flow);

        if (flow == REQUEST) {
            try {
                // TODO get URIFactory from Router
                Map<String, String> queryParams = URLParamUtil.getParams(new URIFactory(),exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                parameters.put("params",queryParams);
            } catch (Exception e) {
                log.info("Cannot parse query parameter from " + exc.getRequest().getUri());
            }
        }

        if (msg != null) {
            parameters.put("message", msg);
            parameters.put("header", msg.getHeader());
            if (includeJsonObject) {
                try {
                    log.info("Parsing body as JSON for scripting plugins");
                    parameters.put("json",om.readValue(readInputStream(msg.getBodyAsStream()),Map.class));  // @Todo not with Javascript
                } catch (Exception e) {
                    log.warn("Can't parse body as JSON: " + e);
                }
            }
        }

        parameters.put("properties", exc.getProperties());

        return parameters;
    }
}
