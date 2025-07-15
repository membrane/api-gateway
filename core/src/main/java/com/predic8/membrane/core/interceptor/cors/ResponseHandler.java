package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * Handles CORS response processing by setting appropriate CORS headers
 * on the response when the origin is allowed or when all origins are permitted.
 * <p>
 * This handler is typically used in the response phase of CORS processing
 * to add necessary headers like Access-Control-Allow-Origin,
 * Access-Control-Allow-Credentials, etc.
 */
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
