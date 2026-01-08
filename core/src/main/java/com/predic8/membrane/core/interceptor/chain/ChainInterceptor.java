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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.util.*;
import org.springframework.beans.factory.*;

import java.util.*;

/**
 * @description A Chain groups multiple interceptors into reusable components, reducing redundancy in API configurations.
 */
@MCElement(name = "chain")
public class ChainInterceptor extends AbstractFlowWithChildrenInterceptor {

    private String ref;

    @Override
    public void init() {
        interceptors = getInterceptorChainForRef(ref);

        super.init();
    }

    private List<Interceptor> getInterceptorChainForRef(String ref) {
        return getBean(ref, ChainDef.class)
                .orElseThrow(() -> new ConfigurationException("No chain found for reference: " + ref))
                .getFlow();
    }

    /**
     * TODO: Temporary fix till we have a central configuration independant lookup
     */
    private <T> Optional<T> getBean(String name, Class<T> clazz) {
        if (router.getRegistry() != null) {
            var bean = router.getRegistry().getBean(name, clazz);
            if (bean.isPresent())
                return bean;
        }
        // From XML
        if (router.getBeanFactory() != null) {
            try {
                return Optional.of(router.getBeanFactory().getBean(name, clazz));
            } catch (NoSuchBeanDefinitionException ignored) {
            }
        }
        return Optional.empty();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return router.getFlowController().invokeRequestHandlers(exc, interceptors);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return router.getFlowController().invokeResponseHandlers(exc, interceptors);
    }

    /**
     * @description The id of the referenced chain.
     */
    @Required
    @MCAttribute
    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getRef() {
        return ref;
    }

}
