/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Sets the destination URL for the exchange. This overrides any previous destination.
 * @topic 1. Proxies and Flow
 */
@MCElement(name = "destination")
public class DestinationInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DestinationInterceptor.class.getName());

    private String url;

    @Override
    public void init() {
        super.init();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        exc.getDestinations().clear();
        exc.getDestinations().add(url);
        exc.setOriginalRequestUri(url);
        log.debug("Set destination to: {}", url);
        return CONTINUE;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @default (not set)
     * @description The backend URL which will be called when the Exchange hits the &lt;httpClient&gt;.
     * @example https://api.predic8.de
     */
    @SuppressWarnings("unused")
    @Required
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public EnumSet<Flow> getAppliedFlow() {
        return Flow.Set.REQUEST_FLOW;
    }
}
