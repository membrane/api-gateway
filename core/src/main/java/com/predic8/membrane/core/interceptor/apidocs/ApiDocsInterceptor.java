package com.predic8.membrane.core.interceptor.apidocs;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCElement(name = "apiDocs")
public class ApiDocsInterceptor extends AbstractInterceptor {

    public ApiDocsInterceptor() {
        name = "Api Docs";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

    }
}
