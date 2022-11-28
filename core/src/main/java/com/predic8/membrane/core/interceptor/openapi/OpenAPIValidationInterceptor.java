package com.predic8.membrane.core.interceptor.openapi;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.slf4j.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_UTF8;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

@MCElement(name="OpenAPIValidator")
public class OpenAPIValidationInterceptor extends AbstractInterceptor {

    private static Logger log = LoggerFactory.getLogger(OpenAPIValidationInterceptor.class.getName());

    OpenAPIValidator validator;

    private String location;
    private boolean validateRequest = true;
    private boolean validateResponse = false;

    public String getLocation() {
        return location;
    }

    public OpenAPIValidationInterceptor() {
        System.out.println("location = " + location);
    }

    @Override
    public void init(Router router) throws Exception {
        super.init(router);

        validator = new OpenAPIValidator(location );
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    @MCAttribute
    public void setValidateRequest(boolean validate) {
        this.validateRequest = validate;
    }

    @MCAttribute
    public void setValidateResponse(boolean validate) {
        this.validateResponse = validate;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        if (!validateRequest)
            return CONTINUE;

        log.info("exc.getRequestURI() = " + exc.getRequestURI());
        Request req = Utils.getOpenapiValidatorRequest(exc);
        log.info("Request: " + req);
        ValidationErrors errors = validator.validate(req);
        if (errors.size() == 0)
            return CONTINUE;

        exc.setResponse(Response.ResponseBuilder.newInstance().status(400,"Bad Request").body(errors.toString()).contentType(APPLICATION_JSON_UTF8).build());

        return RETURN;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {

        if (!validateResponse) {
            return CONTINUE;
        }

        log.info("Validating response.");

        ValidationErrors errors = validator.validateResponse(Utils.getOpenapiValidatorRequest(exc), Utils.getOpenapiValidatorResponse(exc));
        if (errors.size() == 0)
            return CONTINUE;

        exc.setResponse(Response.internalServerError().body(errors.toString()).contentType(APPLICATION_JSON_UTF8).build());

        return RETURN;
    }

//    private Request getOpenapiValidatorRequest(Exchange exc) throws IOException {
//        Request request = new Request(exc.getRequest().getMethod()).path(exc.getRequestURI());
//        if (!exc.getRequest().isBodyEmpty()) {
//            request.body(exc.getRequest().getBodyAsStream());
//        }
//        return request;
//    }
//
//    private com.predic8.membrane.core.openapi.model.Response getOpenapiValidatorResponse(Exchange exc) throws IOException {
//        com.predic8.membrane.core.openapi.model.Response response = com.predic8.membrane.core.openapi.model.Response.statusCode(exc.getResponse().getStatusCode());
//
//        if (!exc.getResponse().isBodyEmpty()) {
//            response.body(exc.getResponse().getBodyAsStream());
//        }
//        return response;
//    }
}
