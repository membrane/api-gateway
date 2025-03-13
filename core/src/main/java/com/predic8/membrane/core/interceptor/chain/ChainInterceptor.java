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

package com.predic8.membrane.core.interceptor.chain;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbstractFlowInterceptor;
import com.predic8.membrane.core.util.ConfigurationException;

/**
 * Interceptor for applying a predefined chain of interceptors to requests and responses.
 *
 * <p>Chains group multiple interceptors into reusable components, reducing redundancy in API configurations.</p>
 *
 * <ul>
 *     <li>Finds and applies the referenced chain using its ID.</li>
 *     <li>Handles both request and response processing.</li>
 * </ul>
 */
@MCElement(name = "chain")
public class ChainInterceptor extends AbstractFlowInterceptor {

    private String id;

    @Override
    public void init() {
        interceptors = router.getBeanFactory()
                .getBeansOfType(Chain.class)
                .values()
                .stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElseThrow(() ->
                        new ConfigurationException("No chain with reference %s found".formatted(id))
                )
                .getInterceptors();

        super.init();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return router.getFlowController().invokeRequestHandlers(exc, interceptors);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return router.getFlowController().invokeResponseHandlers(exc, interceptors);
    }

    @Required
    @MCAttribute
    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

}
