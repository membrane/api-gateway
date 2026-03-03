/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.text;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import org.slf4j.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.core.util.xml.NormalizeXMLForJsonUtil.*;

public class ToJsonSerializer {

    private static final Logger log = LoggerFactory.getLogger(ToJsonSerializer.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ToJsonSerializer() {}

    public static String toJson(Object o) {
         try {
            if (o instanceof NodeList || o instanceof Node) {
                o = normalizeForJson(o);
            }
            return objectMapper.writeValueAsString(o);
        } catch (Exception first) {
            // Fallback: always return valid JSON, even for unsupported types (e.g. java.time.* without modules).
            log.debug("Failed to convert object to JSON. Falling back to JSON string.", first);
            try {
                return objectMapper.writeValueAsString(String.valueOf(o));
            } catch (Exception fallback) {
                log.info("Failed to convert fallback value to JSON.", fallback);
                return "null";
            }
        }
    }
}
