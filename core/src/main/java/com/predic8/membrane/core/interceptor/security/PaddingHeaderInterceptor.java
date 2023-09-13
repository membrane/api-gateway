package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import java.security.SecureRandom;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.EnumSet.of;


// TODO JAVADOC!
/**
 * Uses secure random function for the randmon length according to FIPS 140-2 standard.
 */
@MCElement(name = "paddingHeader")
public class PaddingHeaderInterceptor extends AbstractInterceptor {
    public static final String X_PADDING = "X-Padding";
    private int roundUp = 20;
    private int constant = 5;
    private int random = 10;

    private static final char[] LOOKUP_TABLE = generateLookupTable();
    private final SecureRandom secRdm = new SecureRandom();

    private void setInterceptorMeta() {
        name = "Padding Header";
        setFlow(of(REQUEST, RESPONSE));
    }

    public PaddingHeaderInterceptor() {
    }

    public PaddingHeaderInterceptor(Integer roundUp, Integer constant, Integer random) {
        setInterceptorMeta();
        this.roundUp = roundUp;
        this.constant = constant;
        this.random = random;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg) {
        msg.getHeader().add(X_PADDING,httpCryptoSafePadding(calculatePaddingSize(msg)));
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

    // Compare as:
    // String concat s + s
    // StringWriter or StringBuffer
    // char[] as is
    // for 100_000_000
    // Write a Test that messure this
    // Timer
    public String httpCryptoSafePadding(int len) {
        char[] result = new char[len];

        for (int i = 0; i < len; i++) {
            result[i] = getRandomChar();
        }

        return new String(result);
    }

    private char getRandomChar() {
        return LOOKUP_TABLE[secRdm.nextInt(LOOKUP_TABLE.length)];
    }

    // TODO Test HTTP Safe 0..9, $-_.+!*'(),
    static char[] generateLookupTable() {
        char[] chars = new char[62];
        int index = 0;

        for (char c = 'a'; c <= 'z'; c++) {
            chars[index++] = c;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            chars[index++] = c;
        }
        for (char c = '0'; c <= '9'; c++) {
            chars[index++] = c;
        }

        return chars;
    }

    @MCAttribute
    public void setRoundUp(int roundUp) {
        this.roundUp = roundUp;
    }
    @SuppressWarnings("unused")
    public Integer getRoundUp() {
        return roundUp;
    }

    @MCAttribute
    public void setConstant(int constant) {
        this.constant = constant;
    }
    @SuppressWarnings("unused")
    public Integer getConstant() {
        return constant;
    }

    @MCAttribute
    public void setRandom(int random) {
        this.random = random;
    }
    public Integer getRandom() {
        return random;
    }

}
