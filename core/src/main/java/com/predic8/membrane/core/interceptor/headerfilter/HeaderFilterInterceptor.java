/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.Action.*;

/**
 * @description Removes message headers matching a list of patterns.
 * The first matching child element will be acted upon by the filter.
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "headerFilter", noEnvelope = true)
public class HeaderFilterInterceptor extends AbstractInterceptor {

    private HeaderFilter filter = new HeaderFilter();

    public HeaderFilterInterceptor() {
        name = "header filter";
    }

    @Override
    public String getShortDescription() {
        return "Filters message headers using a list of regular expressions.";
    }

    public enum Action {KEEP, REMOVE}

    /**
     * @description Contains a Java regex for <i>including</i> message headers.
     */
    @MCElement(name = "include", collapsed = true)
    public static class Include extends HeaderFilterRule {
        public Include() {
            super(KEEP);
        }
    }

    /**
     * @description Contains a Java regex for <i>excluding</i> message headers.
     */
    @MCElement(name = "exclude", collapsed = true)
    public static class Exclude extends HeaderFilterRule {
        public Exclude() {
            super(REMOVE);
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        filter(exc.getRequest());
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        filter(exc.getResponse());
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exchange) {
        filter(exchange.getResponse());
    }

    private void filter(Message msg) {
        filter.filter(msg.getHeader());
    }

    public List<HeaderFilterRule> getFilterRules() {
        return filter.getRules();
    }

    /**
     * @description List of actions to take (either allowing or removing HTTP headers).
     */
    @Required
    @MCChildElement
    public void setFilterRules(List<HeaderFilterRule> headerFilterRules) {
        filter.setRules(headerFilterRules);
    }

}