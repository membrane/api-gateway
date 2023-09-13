package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import java.security.SecureRandom;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

@MCElement(name = "paddingHeader")
public class PaddingHeaderInterceptor extends AbstractInterceptor {
    private Integer roundUp;

    private Integer constant;
    private Integer random;

    private static final char[] LOOKUP_TABLE = generateLookupTable();
    private final SecureRandom secRdm = new SecureRandom();

    public PaddingHeaderInterceptor(Integer roundUp, Integer constant, Integer random) {
        this.roundUp = roundUp;
        this.constant = constant;
        this.random = random;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.getRequest().setHeader(new Header("X-Padding: " + httpCryptoSafePadding(calculatePaddingSize(exc.getRequest()))));
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        exc.getResponse().setHeader(new Header("X-Padding: " + httpCryptoSafePadding(calculatePaddingSize(exc.getResponse()))));
        return CONTINUE;
    }

    private int calculatePaddingSize(Message msg) {
       return getRoundUp() % msg.estimateHeapSize() + getConstant() + secRdm.nextInt(0, getRandom()-1);
    }

    private char[] randomizeLookupTable() {
        char[] randomized = LOOKUP_TABLE.clone();

        for (int i = randomized.length - 1; i > 0; i--) {
            int j = secRdm.nextInt(i + 1);
            char temp = randomized[i];
            randomized[i] = randomized[j];
            randomized[j] = temp;
        }

        return randomized;
    }

    public String httpCryptoSafePadding(int len) {
        char[] result = new char[len];

        for (int i = 0; i < len; i++) {
            int rdmIdx = secRdm.nextInt(LOOKUP_TABLE.length);
            result[i] = LOOKUP_TABLE[rdmIdx];
        }

        return new String(result);
    }

    private static char[] generateLookupTable() {
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
    public void setRoundUp(Integer roundUp) {
        this.roundUp = roundUp;
    }
    public Integer getRoundUp() {
        return roundUp;
    }

    @MCAttribute
    public void setConstant(Integer constant) {
        this.constant = constant;
    }
    public Integer getConstant() {
        return constant;
    }

    @MCAttribute
    public void setRandom(Integer random) {
        this.random = random;
    }
    public Integer getRandom() {
        return random;
    }

}
