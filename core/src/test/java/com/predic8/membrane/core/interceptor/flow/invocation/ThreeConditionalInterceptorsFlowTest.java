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

public class ThreeConditionalInterceptorsFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {


        ConditionalInterceptor ci1 = new ConditionalInterceptor();
        ci1.setTest("true");
        ci1.getInterceptors().add(new FlowTestInterceptor("c1"));

        ConditionalInterceptor ci2 = new ConditionalInterceptor();
        ci2.setTest("true");
        ci2.getInterceptors().add(new FlowTestInterceptor("c2"));

        ConditionalInterceptor ci3 = new ConditionalInterceptor();
        ci3.setTest("true");
        ci3.getInterceptors().add(new FlowTestInterceptor("c3"));

        return List.of(new FlowTestInterceptor("a"),
                ci1,
                ci2,
                ci3,
                new FlowTestInterceptor("d"));
    }

    @Override
    protected String flow() {
        return ">a>c1>c2>c3>d<d<c3<c2<c1<a";
    }
}
