package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.EmptyBody;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;

import java.io.IOException;

@MCElement(name="methodOverride")
public class MethodOverrideInterceptor extends AbstractInterceptor {



    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        String methodHeader = exc.getRequest().getHeader().getFirstValue(Header.X_HTTP_METHOD_OVERRIDE);
        if(methodHeader == null)
            return Outcome.CONTINUE;

        switch(methodHeader){
            case "GET": handleGet(exc);
                        break;
        }

        exc.getRequest().getHeader().removeFields(Header.X_HTTP_METHOD_OVERRIDE);

        return Outcome.CONTINUE;
    }

    private void handleGet(Exchange exc) throws IOException {
        Request req = exc.getRequest();
        req.readBody();
        req.setBody(new EmptyBody());
        req.getHeader().removeFields(Header.CONTENT_LENGTH);
        req.getHeader().removeFields(Header.CONTENT_TYPE);
        req.setMethod("GET");
    }
}
