package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import java.security.SecureRandom;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.EnumSet.of;

@MCElement(name = "paddingHeader")
public class PaddingHeaderInterceptor extends AbstractInterceptor {
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
        setInterceptorMeta();
    }

    public PaddingHeaderInterceptor(Integer roundUp, Integer constant, Integer random) {
        setInterceptorMeta();

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

    private char[] randomizeCharArray(char[] lookup) {
        char[] randomized = lookup.clone();

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

        char[] randomTable = randomizeCharArray(LOOKUP_TABLE);

        for (int i = 0; i < len; i++) {
            int rdmIdx = secRdm.nextInt(randomTable.length);
            result[i] = randomTable[rdmIdx];
        }

        return new String(result);
    }

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
    public Integer getRoundUp() {
        return roundUp;
    }

    @MCAttribute
    public void setConstant(int constant) {
        this.constant = constant;
    }
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