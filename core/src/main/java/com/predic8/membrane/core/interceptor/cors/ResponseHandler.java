package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

public class ResponseHandler extends AbstractCORSHandler {

    public ResponseHandler(CorsInterceptor interceptor) {
        super(interceptor);
    }

    @Override
    public Outcome handleInternal(Exchange exc, String origin) {

        if (interceptor.isAllowAll() || originAllowed(origin)) {
            setCORSHeader(exc, origin);
        }

        // Not allowed => Do not set any allow headers
        return CONTINUE;
    }

    @Override
    protected String getRequestMethod(Exchange exc) {
        return exc.getRequest().getMethod();
    }

}
