/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.kubernetes.model.AdmissionResponse;
import com.predic8.membrane.core.interceptor.kubernetes.model.AdmissionReview;
import com.predic8.membrane.core.interceptor.kubernetes.model.JSONValidatorError;
import com.predic8.membrane.core.interceptor.kubernetes.model.ResponseStatus;
import com.predic8.membrane.core.interceptor.schemavalidation.IValidator;
import com.predic8.membrane.core.interceptor.schemavalidation.JSONValidator;
import com.predic8.membrane.core.resolver.ResolverMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;

@MCElement(name="kubernetesValidation")
public class KubernetesValidationInterceptor extends AbstractInterceptor {

    private IValidator validator;
    private ResolverMap resourceResolver;

    @Override
    public void init(Router router) throws Exception {
        resourceResolver = router.getResolverMap();
        super.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (exc.getRequest().isBodyEmpty())
            return Outcome.CONTINUE;

        ObjectMapper mapper = new ObjectMapper();
        AdmissionReview review = mapper.readValue(new BufferedReader(new InputStreamReader(
                        exc.getRequest().getBodyAsStreamDecoded(), Charset.forName(exc.getRequest().getCharset())))
                , AdmissionReview.class);
        String requestKind = (String) review.getRequest().getObject().get("kind");
        setValidator(requestKind.toLowerCase());

        validator.validateMessage(exc, exc.getRequest(), "request");

        setExchangeResponse(exc, mapper, review);

        return Outcome.RETURN;
    }

    private void setValidator(String schema) throws IOException {
        String baseLocation = router == null ? null : router.getBaseLocation();
        validator = new JSONValidator(resourceResolver,
                ResolverMap.combine(baseLocation, "kubernetes/" + schema + ".schema.json"), null);
    }

    private void setExchangeResponse(Exchange exc, ObjectMapper mapper, AdmissionReview review) throws Exception {
        AdmissionResponse response = new AdmissionResponse(review.getRequest().getUid());
        review.setResponse(response);

        if (exc.getResponse() == null) {
            response.setAllowed(true);
        } else {
            response.setAllowed(false);

            List<String> errors = mapper
                    .readValue(exc.getResponse().getBody().toString(), JSONValidatorError.class)
                    .getErrors();

            response.setStatus(new ResponseStatus(
                    String.join(", ", errors)
            ));
        }

        review.setRequest(null);
        exc.setResponse(Response.ok(
                mapper.writeValueAsString(review)
        ).build());
    }
}
