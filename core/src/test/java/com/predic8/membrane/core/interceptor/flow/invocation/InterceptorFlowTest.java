package com.predic8.membrane.core.interceptor.flow.invocation;

import org.junit.jupiter.api.*;

/**
 * Tests the invocation of interceptors in different flows
 * Expectations about the flow of interceptors is described as string from left to right.
 * >a = interceptor a in request flow
 * <a = interceptor a in response flow
 * ?a = interceptor a in abort flow
 */
public class InterceptorFlowTest extends AbstractInterceptorFlowTest {

    public static final String TRUE = "true";

    @Test
    void normalOne() throws Exception {
        assertFlow(">a<a", A);
    }

    @Test
    void normalTwo() throws Exception {
        assertFlow(">a>b<b<a", A, B);
    }

    @Test
    void request() throws Exception {
        assertFlow(">a>b>c<c<a", A, REQUEST(B), C);
    }

    @Test
    void response() throws Exception {
        assertFlow(">a>c<c<b<a", A, RESPONSE(B), C);
    }

    @Test
    void normalReturn() throws Exception {
        assertFlow(">a<a", A, RETURN, B);
    }

    @Test
    void responseReturn() throws Exception {
        assertFlow(">a>b<b<a", A, RESPONSE(RETURN), B);
    }

    @Test
    void requestReturn() throws Exception {
        assertFlow(">a<a", A, REQUEST(RETURN), B);
    }

    @Test
    void normalIgnoreAbort() throws Exception {
        assertFlow(">a>c<c<a", A, ABORT(B), C);
    }

    @Test
    void flowAborts() throws Exception {
        assertFlow(">a?a", A, ABORT, B);
    }

    @Test
    void abortException() throws Exception {
        assertFlow(">a>c?c<b?a", A, ABORT(B), C, EXCEPTION, ABORT(B), C, EXCEPTION);
    }

    @Test
    void ifFalse() throws Exception {
        assertFlow(">a>c<c<a", A, IF("false", B), C);
    }

    @Test
    void exception() throws Exception {
        assertFlow(">a?a", A, EXCEPTION, B);
    }

    @Test
    void ifRequestResponseWithException() throws Exception {
        assertFlow(">a>i1?a", A,
                REQUEST(IF(TRUE,I1)),
                RESPONSE(IF(TRUE,I2)),
                EXCEPTION);
    }

    @Test
    void requestResponseIf() throws Exception {
        assertFlow(">a>i1>b<b<i2<a", A, REQUEST(IF("true", I1)), RESPONSE(IF("true",I2)), B);
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
                REQUEST(IF(TRUE, I1)),B,
                RESPONSE(IF(TRUE, I2)), C,
                REQUEST(IF(TRUE, I3)),
                D,
                RESPONSE(IF(TRUE, I4)),
                E);
    }

}
