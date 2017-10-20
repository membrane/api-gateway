package com.predic8.membrane.core.interceptor.registration;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;

/**
 * Created by Martin DŁnkelmann(duenkelmann@predic8.de) on 20.10.17.
 */
public class ErrorMessages {
    public static Outcome returnErrorUserAlreadyExists(Exchange exc) {
        exc.setResponse(Response.badRequest("User already exists.").build());
        return Outcome.RETURN;
    }

    public static Outcome returnErrorBadRequest(Exchange exc) {
        exc.setResponse(Response.badRequest("POST-Request with the following body is needed for account registration:\t {\"email\":\"E-Mail\",\"password\":\"Password\"}").build());
        return Outcome.RETURN;
    }
}
