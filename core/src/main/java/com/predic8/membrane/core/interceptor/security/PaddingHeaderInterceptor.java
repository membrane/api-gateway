package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCElement(name = "paddingHeader")
public class PaddingHeaderInterceptor extends AbstractInterceptor {

    private Integer roundUp;
    private Integer constant;
    private Integer random;

    public PaddingHeaderInterceptor(Integer roundUp, Integer constant, Integer random) {
        this.roundUp = roundUp;
        this.constant = constant;
        this.random = random;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return super.handleRequest(exc);
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
