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

package com.predic8.membrane.core.interceptor.flow.invocation.internalservice;

import com.predic8.membrane.core.rules.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static org.junit.jupiter.api.Assertions.*;

public class ServiceCallsServiceInternalRoutingTest extends AbstractInternalServiceRoutingInterceptorTest {

    protected void configure() throws Exception {

        api(a -> {
            a.setKey(new ServiceProxyKey("*","*",null,2000));
            a.add(A);
            a.setTargetURL("service://b");
        });

        api(b -> {
            b.setName("b");
            b.add(B);
            b.setTargetURL("service://c");
        });

        api(c -> {
            c.setName("c");
            c.add(C);
            c.setTargetURL("service://d");
        });

        api(d -> {
            d.setName("d");
            d.add(D,RETURN);
        });
    }

    @Test
    void test() throws Exception {
        assertEquals(">a>b>c>d<d<c<b<a", call());
    }
}
