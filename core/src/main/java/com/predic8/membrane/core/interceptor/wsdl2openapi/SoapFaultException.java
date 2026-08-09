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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import java.util.Map;

/**
 * Thrown by {@link Soap2JsonTransformer} when the SOAP response body contains a {@code <Fault>} element.
 * Carries the fault code and the HTTP status code implied by the fault type:
 * {@code soap:Client}/{@code env:Sender} 400, everything else 500.
 * {@code soapDetail} holds the content of the SOAP {@code <detail>}/{@code <Detail>} element (may be null).
 */
class SoapFaultException extends Exception {

    private final String faultCode;
    private final int httpStatus;
    private final Map<String, Object> soapDetail;

    SoapFaultException(String faultCode, String faultMessage, int httpStatus, Map<String, Object> soapDetail) {
        super(faultMessage);
        this.faultCode = faultCode;
        this.httpStatus = httpStatus;
        this.soapDetail = soapDetail;
    }

    String getFaultCode() {
        return faultCode;
    }

    String getFaultMessage() {
        return getMessage();
    }

    int getHttpStatus() {
        return httpStatus;
    }

    Map<String, Object> getSoapDetail() {
        return soapDetail;
    }
}
