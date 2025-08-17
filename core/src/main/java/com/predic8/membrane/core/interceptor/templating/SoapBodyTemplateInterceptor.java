/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.util.soap.*;

import static com.predic8.membrane.core.util.soap.SoapVersion.*;

/**
 * @description Renders a SOAP body for legacy intergration
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name="soapBody", mixed = true)
public class SoapBodyTemplateInterceptor extends TemplateInterceptor {

    private SoapVersion version = SOAP_11;

    private static final String SOAP11_PREFIX = """
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                    <s11:Body>
                """;

    private static final String SOAP11_POSTFIX = """
                    </s11:Body>
                </s11:Envelope>
                """;

    private static final String SOAP12_PREFIX = """
                <s12:Envelope xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                    <s12:Body>
                """;

    private static final String SOAP12_POSTFIX = """
                    </s12:Body>
                </s12:Envelope>
                """;
    @Override
    protected byte[] getContent(Exchange exc, Flow flow) {
        return (switch (version) {
            case SOAP_11 -> SOAP11_PREFIX + asString( super.getContent(exc, flow)) + SOAP11_POSTFIX;
            case SOAP_12 -> SOAP12_PREFIX +asString(  super.getContent(exc, flow)) + SOAP12_POSTFIX;
        }).getBytes();
    }

    @Override
    public String getContentType() {
        return version.getContentType();
    }

    public String getVersion() {
        return version.toString();
    }

    @MCAttribute
    public void setVersion(String version) {
        this.version = SoapVersion.parse(version);
    }
}
