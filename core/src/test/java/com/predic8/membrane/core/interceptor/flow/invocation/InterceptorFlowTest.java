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

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the invocation of interceptors in different flows
 * Expectations about the flow of interceptors is described as string from left to right.
 * >a = interceptor a in request flow
 * <a = interceptor a in response flow
 * ?a = interceptor a in abort flow
 */
public class InterceptorFlowTest extends AbstractInterceptorFlowTest {

    public static final String TRUE = "true";
    public static final String FALSE = "false";

    @Test
    void one() throws Exception {
        assertFlow(">a<a", A);
    }

    @Test
    void two() throws Exception {
        assertFlow(">a>b<b<a", A, B);
    }

    @Test
    void request() throws Exception {
        assertFlow(">a>b>c>d<d<a", A, REQUEST(B,C), D);
    }

    @Test
    void response() throws Exception {
        assertFlow(">a>d<d<c<b<a", A, RESPONSE(B,C), D);
    }

    @Test
    void normalReturn() throws Exception {
        assertFlow(">a<a", A, RETURN, B);
    }

    @Test
    void responseReturn() throws Exception {
        assertFlow(">a>b<a", A, REQUEST(B, RETURN, C), D);
    }

    @Test
    void normalAbort() throws Exception {
        assertFlow(">a>c<c<a", A, ABORT(B), C);
    }

    @Test
    void flowAborts() throws Exception {
        assertFlow(">a?a", A, ABORT, B);
    }

    @Test
    void exception() throws Exception {
        assertTrue(getResponse(A, EXCEPTION, B).endsWith("?a"));
    }

    @Test
    void abortException() throws Exception {
        assertTrue(getResponse(A, ABORT(B), C, EXCEPTION, ABORT(B), C).endsWith("?c<b?a"));
    }

    @Test
    void ifTrue() throws Exception {
        assertFlow(">a>b>c<c<b<a", A, IF("true", B), C);
    }

    @Test
    void ifFalse() throws Exception {
        assertFlow(">a>c<c<a", A, IF(FALSE, B), C);
    }

    @Test
    void ifRequestResponseWithException() throws Exception {
        assertTrue(getResponse(A,
                REQUEST(IF(TRUE, I1)),
                RESPONSE(IF(TRUE, I2)),
                EXCEPTION).endsWith("?a"));
    }

    @Test
    void requestResponseIf() throws Exception {
        assertFlow(">a>i1>b<b<i2<a", A,
                REQUEST(IF("true", I1)),
                RESPONSE(IF("true", I2)),
                B);
    }

    @Test
    void abortInRequest() throws Exception {
        // Note: ">a>c<b?a" is correct not ">a>c?b?a" because the <abort>-interceptor
        // calls handleResponse() on it's children
        assertFlow(">a>c<b?a", A, ABORT(B), REQUEST(C, ABORT, D), E);
    }

    @Test
    void abortInResponse() throws Exception {
        // Note: ">a>e<e<c?a" is correct not ">a>e<e?c?a" because the <abort>-interceptor
        // calls handleResponse() on it's children
        // D is not invoked cause before there is an ABORT and it is in a response interceptor
        assertFlow(">a>e<e<c?a", A, RESPONSE(B), ABORT(C), RESPONSE( D,ABORT), E);
    }

    @Test
    void n6() throws Exception {
        assertFlow(">a>i1>i2>i3>d<d<i3<i2<i1<a", A,
                IF("true", I1),
                IF("true", I2),
                IF("true", I3),
                D);
    }

    @Test
    void complexRequestResponseIf() throws Exception {
        assertFlow(">a>i1>b>c>i3>d>e<e<i4<d<c<i2<b<a", A,
                REQUEST(IF(TRUE, I1)), B,
                RESPONSE(IF(TRUE, I2)), C,
                REQUEST(IF(TRUE, I3)),
                D,
                RESPONSE(IF(TRUE, I4)),
                E);
    }

    @Test
    void exceptionInIf() throws Exception {
        assertTrue(getResponse(A,
                REQUEST(IF(TRUE, C, EXCEPTION)),
                B).endsWith("?a"));
    }

    @Test
    void ifWhenAborted() throws Exception {
        assertFlow(">a>i1>i2?a",
                A,
                IF(TRUE, I1, I2),
                ABORT);
    }

    @Test
    void ifInAbort() throws Exception {
        assertFlow(">a<i2<i1?a",
                A,
                ABORT(IF(TRUE, I1, I2)),
                ABORT);
    }

    @Test
    void groovy() throws Exception {
        assertFlow(">a<a",
                A,
                GROOVY("RETURN"),
                B);
    }

    @Test
    void groovyInAbortFlow() throws Exception {
        assertFlow(">a?GroovyAbort?a",
                A,
                GROOVY("""
                    if (!flow.isAbort())
                        return
                    Response.ok(message.getBodyAsStringDecoded()+"?GroovyAbort").build();
                    """),
                ABORT);
    }
}