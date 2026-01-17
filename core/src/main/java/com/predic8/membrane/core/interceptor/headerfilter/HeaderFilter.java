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

package com.predic8.membrane.core.interceptor.headerfilter;

import com.predic8.membrane.core.http.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.Action.REMOVE;

public class HeaderFilter {

    private static final Logger log = LoggerFactory.getLogger(HeaderFilter.class);

    private List<HeaderFilterRule> rules = new ArrayList<>();

    void filter(Header h) {
        for (HeaderField hf : h.getAllHeaderFields()) {
            for (HeaderFilterRule r : rules) {
                // When the rule matches carry on with the next header field
                // otherwise check the next rule
                if (filterField(h, hf, r)) break;
            }
        }
    }

    /**
     * @param header The header with all header fields.
     * @param headerField The header field to check.
     * @param rule The rule to check.
     * @return true if the header field has matched => continue with the next header field
     */
    private static boolean filterField(Header header, HeaderField headerField, HeaderFilterRule rule) {
        if (!rule.matches(headerField))
            return false;

        if (rule.getAction() == REMOVE) {
            log.debug("Removing HTTP header {}", headerField.getHeaderName().toString());
            header.remove(headerField);
        }
        return true;
    }

    public List<HeaderFilterRule> getRules() {
        return rules;
    }

    public void setRules(List<HeaderFilterRule> rules) {
        this.rules = rules;
    }
}
