package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.HttpUtil.getMessageForStatusCode;


/**
 * @description Terminates the request and returns the current request body as content. Useful together with the
 * template interceptor e.g. in examples.
 *
 * @topic 4. Interceptors/Features
 */
@MCElement(name="return")
public class ReturnInterceptor extends AbstractInterceptor {

    private int statusCode = 200;
    private String contentType = null;

    @MCAttribute
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @MCAttribute
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setResponse(new Response.ResponseBuilder().status(statusCode,getMessageForStatusCode(statusCode)).contentType(getContentType(exc)).bodyEmpty().build());
        return RETURN;
    }

    private String getContentType(Exchange exc) {
        if (contentType != null)
            return contentType;

        if (exc.getRequest().getHeader().getContentType() != null)
            return exc.getRequest().getHeader().getContentType();

        return "text/plain";
    }



    @Override
    public String getDisplayName() {
        return "Return";
    }

    @Override
    public String getShortDescription() {
        return String.format("Sends an response with a status code of %d and an content type of %s.",statusCode,contentType);
    }
}
