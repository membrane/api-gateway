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

import java.util.*;

public class ThreeConditionalInterceptorsFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {
        return List.of(A,
                IF("true", I1),
                IF("true", I2),
                IF("true", I3),
                D);
    }

    @Override
    protected String flow() {
        return ">a>i1>i2>i3>d<d<i3<i2<i1<a";
    }
}
