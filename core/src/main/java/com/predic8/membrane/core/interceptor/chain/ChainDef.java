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
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.Interceptor;

import java.util.List;

/**
 * @description Defines a reusable chain of interceptors that can be applied to multiple APIs.
 */
@MCElement(name = "chainDef")
public class ChainDef {

    private String id;

    List<Interceptor> interceptors;

    /**
     * @description The list of interceptors to be executed in sequence.
     */
    @MCChildElement
    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    /**
     * @description The id for referencing the chain.
     */
    @MCAttribute
    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

}
