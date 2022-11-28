package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;


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

    @MCAttribute
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setResponse(new Response.ResponseBuilder().status(statusCode,getMessageForStatusCode(statusCode)).contentType(getContentType(exc)).build());
        return RETURN;
    }

    private String getContentType(Exchange exc) {
        if (contentType != null)
            return contentType;

        if (exc.getRequest().getHeader().getContentType() != null)
            return exc.getRequest().getHeader().getContentType();

        return "text/plain";
    }

    /**
     * @Todo Move to ResponseBuilder?, duplicated?
     * @param code
     * @return
     */
    private String getMessageForStatusCode(int code) {
        switch (code) {
            case 100: return "Continue";
            case 200: return "Ok";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 206: return "Partial Content";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 304: return "Not Modified";
            case 307: return "Temporary Redirect";
            case 308: return "Permanent Redirect";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method not Allowed";
            case 409: return "Conflict";
            case 415: return "Unsupported Mediatype";
            case 422: return "Unprocessable Entity";
            case 500: return "Internal Server Error";
            case 501: return "Not Implemented";
            case 502: return "Bad Gateway";
            default: return "";
        }
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
