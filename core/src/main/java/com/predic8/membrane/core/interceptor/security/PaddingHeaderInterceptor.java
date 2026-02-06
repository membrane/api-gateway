/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */


package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import java.security.SecureRandom;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.EnumSet.of;


/**
 * @description Adds an X-Padding header with random characters to pad message size (request/response).
 * @yaml
 * <pre><code>
 *  api:
 *    flow:
 *      - paddingHeader:
 *          roundUp: 20
 *          constant: 5
 *          random: 10
 * </code></pre>
 */
@MCElement(name = "paddingHeader")
public class PaddingHeaderInterceptor extends AbstractInterceptor {
    static final String LOOKUP_TABLE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _:;.,\\/'?!(){}[]@<>=-+*#$&`|~^%";
    static final String X_PADDING = "X-Padding";
    private int roundUp = 20;
    private int constant = 5;
    private int random = 10;
    private final SecureRandom secRdm = new SecureRandom();

    private void setInterceptorMeta() {
        name = "padding header";
        setAppliedFlow(of(REQUEST, RESPONSE));
    }

    @Override
    public String getShortDescription() {
        return "Generates a randomized header field that artificially pads the message to protect against padding oracle attacks like CVE-2013-3587";
    }

    @SuppressWarnings("unused")
    public PaddingHeaderInterceptor() {
        setInterceptorMeta();
    }

    public PaddingHeaderInterceptor(Integer roundUp, Integer constant, Integer random) {
        setInterceptorMeta();
        this.roundUp = roundUp;
        this.constant = constant;
        this.random = random;
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg) {
        msg.getHeader().add(X_PADDING, headerSafePadding(calculatePaddingSize(msg)));
        return CONTINUE;
    }

    public int calculatePaddingSize(Message msg) {
       return calculatePaddingSize(msg.estimateHeapSize());
    }

    public int calculatePaddingSize(long size) {
        return roundUp(size) + constant + getRandomNumber();
    }

    public int getRandomNumber() {
        return secRdm.nextInt(0, random);
    }

    public int roundUp(long n) {
        return (int)(roundUp - (n % roundUp));
    }

    public String headerSafePadding(int len) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < len; i++) {
            result.append(getRandomChar());
        }

        return result.toString();
    }

    private char getRandomChar() {
        return LOOKUP_TABLE.charAt(secRdm.nextInt(LOOKUP_TABLE.length()));
    }

    /**
     * @description Rounds message size up to the next multiple of this value.
     * @default 20
     * @example 32
     */
    @MCAttribute
    public void setRoundUp(int roundUp) {
        this.roundUp = roundUp;
    }
    @SuppressWarnings("unused")
    public Integer getRoundUp() {
        return roundUp;
    }

    /**
     * @description Constant padding added after rounding.
     * @default 5
     * @example 0
     */
    @MCAttribute
    public void setConstant(int constant) {
        this.constant = constant;
    }
    @SuppressWarnings("unused")
    public Integer getConstant() {
        return constant;
    }

    /**
     * @description Upper bound (exclusive) for random extra padding length.
     * @default 10
     * @example 50
     */
    @MCAttribute
    public void setRandom(int random) {
        this.random = random;
    }
    public Integer getRandom() {
        return random;
    }

}
