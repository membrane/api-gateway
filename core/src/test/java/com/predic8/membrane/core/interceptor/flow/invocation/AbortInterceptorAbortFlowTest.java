/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.flow.invocation.testinterceptors.*;

import java.util.*;

public class AbortInterceptorAbortFlowTest extends AbstractInterceptorFlowTest {

    @Override
    protected List<Interceptor> interceptors() {

        AbortInterceptor ai = new AbortInterceptor();
        ai.getInterceptors().add(new FlowTestInterceptor("b"));

        return List.of(new FlowTestInterceptor("a"),
                ai,
                new FlowTestInterceptor("c"),
                new ExceptionTestInterceptor());
    }

    @Override
    protected String flow() {
        return ">a>c?c<b?a";
    }
}
