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

public class ExceptionRequestResponseConditionalFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {

        ConditionalInterceptor ci1 = new ConditionalInterceptor();
        ci1.setTest("true");
        ci1.getInterceptors().add(new FlowTestInterceptor("c1"));

        RequestInterceptor reqi = new RequestInterceptor();
        reqi.getInterceptors().add(ci1);

        ConditionalInterceptor ci2 = new ConditionalInterceptor();
        ci2.setTest("true");
        ci2.getInterceptors().add(new FlowTestInterceptor("c2"));

        ResponseInterceptor resi = new ResponseInterceptor();
        resi.getInterceptors().add(ci2);

        return List.of(new FlowTestInterceptor("a"),
                reqi,
                resi,
                new ExceptionTestInterceptor());
    }

    @Override
    protected String flow() {
        return ">a>c1?a";
    }
}
