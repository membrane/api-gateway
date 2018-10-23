package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.http.Exchange;
import com.bornium.http.Method;
import com.bornium.http.Request;
import com.bornium.http.Response;
import com.predic8.membrane.core.http.HeaderField;

import java.net.URI;
import java.nio.charset.Charset;

/**
 * Created by Xorpherion on 26.02.2017.
 */
public class Convert {
    public static Response convertFromMembraneResponse(com.predic8.membrane.core.http.Response membraneResponse) {
        if (membraneResponse != null) {
            Response result = new Response();
            result.setStatuscode(membraneResponse.getStatusCode());
            result.setBody(membraneResponse.getBodyAsStringDecoded());
            for (HeaderField header : membraneResponse.getHeader().getAllHeaderFields())
                result.getHeader().append(header.getHeaderName().toString(), header.getValue());
            return result;
        }
        return null;
    }

    public static Request convertFromMembraneRequest(com.predic8.membrane.core.http.Request membraneRequest) {
        if (membraneRequest != null) {
            Request result = new Request();
            result.setUri(URI.create(membraneRequest.getUri()));
            result.setMethod(Method.fromString(membraneRequest.getMethod()));
            result.setBody(membraneRequest.getBodyAsStringDecoded());
            for (HeaderField header : membraneRequest.getHeader().getAllHeaderFields())
                result.getHeader().append(header.getHeaderName().toString(), header.getValue());
            return result;
        }
        return null;
    }

    public static Exchange convertFromMembraneExchange(com.predic8.membrane.core.exchange.Exchange memExc){
        return new Exchange(Convert.convertFromMembraneRequest(memExc.getRequest()),Convert.convertFromMembraneResponse(memExc.getResponse()));
    }

    public static com.predic8.membrane.core.http.Request convertToMembraneRequest(Request request) {
        if (request != null) {
            com.predic8.membrane.core.http.Request result = new com.predic8.membrane.core.http.Request();
            result.setUri(request.getUri().toString());
            result.setMethod(request.getMethod().toString());
            result.setBodyContent(request.getBody().getBytes(Charset.defaultCharset()));
            for (String headername : request.getHeader().getHeaderNames())
                result.getHeader().add(headername, request.getHeader().getValue(headername));
            return result;
        }
        return null;
    }

    public static com.predic8.membrane.core.http.Response convertToMembraneResponse(Response response) {
        if (response != null) {
            com.predic8.membrane.core.http.Response result = new com.predic8.membrane.core.http.Response();
            result.setStatusCode(response.getStatuscode());
            result.setBodyContent(response.getBody().getBytes(Charset.defaultCharset()));
            for (String headername : response.getHeader().getHeaderNames())
                result.getHeader().add(headername, response.getHeader().getValue(headername));
            return result;
        }
        return null;
    }

    public static com.predic8.membrane.core.exchange.Exchange convertToMembraneExchange(Exchange exc){
        com.predic8.membrane.core.exchange.Exchange memExc = new com.predic8.membrane.core.exchange.Exchange(null);
        memExc.setRequest(Convert.convertToMembraneRequest(exc.getRequest()));
        memExc.setResponse(Convert.convertToMembraneResponse(exc.getResponse()));
        return memExc;
    }

}
