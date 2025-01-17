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
package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.interceptor.flow.invocation.AbstractInterceptorFlowTest;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static com.predic8.membrane.core.interceptor.flow.invocation.InterceptorFlowTest.FALSE;
import static com.predic8.membrane.core.interceptor.flow.invocation.InterceptorFlowTest.TRUE;

public class ChoiceInterceptorTest extends AbstractInterceptorFlowTest {

    @Test
    void caseA() throws Exception {
        assertFlow(">a>b<b<a",
            A,
            CHOICE(
                OTHERWISE(D),
                CASE(
                    TRUE,
                    B
                ),
                CASE(
                    FALSE,
                    C
                )
            )
        );
    }

    @Test
    void caseB() throws Exception {
        assertFlow(">a>c<c<a",
            A,
            CHOICE(
                OTHERWISE(D),
                CASE(
                    FALSE,
                    B
                ),
                CASE(
                    TRUE,
                    C
                )
            )
        );
    }

    @Test
    void caseOtherwise() throws Exception {
        assertFlow(">a>d<d<a",
            A,
            CHOICE(
                OTHERWISE(D),
                CASE(
                    FALSE,
                    B
                ),
                CASE(
                    FALSE,
                    C
                )
            )
        );
    }
}