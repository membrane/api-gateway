/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.soap2json;

import com.predic8.membrane.core.http.Response;

import static com.predic8.membrane.core.exceptions.ProblemDetails.problemDetails;

/**
 * Turns a SOAP fault a service answered with into the problem details document clients receive —
 * the same for soap2Json and wsdl2openapi.
 */
public final class SoapFaultProblemDetails {

    /**
     * Problem details subtype for a failed operation. Deliberately says nothing about SOAP: the
     * {@code type} URI reaches the client, and an operator may not want to advertise that a legacy
     * service sits behind the API.
     */
    private static final String OPERATION_ERROR_TYPE = "operation-error";

    /** The problem details member carrying the content of a fault the operation declares. */
    public static final String FAULT_DETAILS_FIELD = "details";

    private SoapFaultProblemDetails() {}

    /**
     * The gateway cannot tell whether the backend rejected the input, broke, or timed out, so a
     * fault is a 500, the most generic status. Nothing in the response names the technology behind
     * the API — the fault code and the backend's fault message stay internal, development-mode aids,
     * because an operator may not want clients to know a legacy service sits behind the gateway, and
     * a faultstring can carry a class name or an internal host name.
     *
     * @param component the display name of the interceptor reporting the fault
     */
    public static Response of(SoapFaultException fault, boolean production, String component) {
        final var pd = problemDetails(OPERATION_ERROR_TYPE, production)
                .component(component)
                .status(500)
                .title("Operation failed")
                .detail("The service could not complete the operation.")
                .internal("faultCode", fault.getFaultCode())
                .internal("faultMessage", fault.getFaultMessage());
        if (fault.getSoapDetail() != null) {
            pd.topLevel(FAULT_DETAILS_FIELD, fault.getSoapDetail());
        }
        return pd.build();
    }
}
