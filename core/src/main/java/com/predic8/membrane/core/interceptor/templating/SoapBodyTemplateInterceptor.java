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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.soap.SoapVersion;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.util.soap.SoapVersion.SOAP_11;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description Wraps a rendered template in a SOAP <code>Envelope</code> and <code>Body</code> and sets it as the
 * message body. The element body is a Groovy template: <code>${...}</code> expressions are substituted with values from
 * the request or response, headers, query parameters, and so on, exactly as with <code>template</code>. The
 * Content-Type is set to match the SOAP version. See the examples under examples/web-services-soap/rest2soap-template
 * and the tutorial tutorials/soap/60-SOAP-Body-Template.yaml.
 * @topic 2. Enterprise Integration Patterns
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - soapBody:
 *         version: '1.2'
 *         src: |
 *           &lt;cs:getCity xmlns:cs="https://predic8.de/cities"&gt;
 *             &lt;name&gt;${params.city}&lt;/name&gt;
 *           &lt;/cs:getCity&gt;
 * </code></pre>
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
            case SOAP_11 -> SOAP11_PREFIX + asString(super.getContent(exc, flow)) + SOAP11_POSTFIX;
            case SOAP_12 -> SOAP12_PREFIX +asString(super.getContent(exc, flow)) + SOAP12_POSTFIX;
        }).getBytes(UTF_8);
    }

    @Override
    public String getContentType() {
        return version.getContentType();
    }

    public String getVersion() {
        return version.toString();
    }

    /**
     * @description SOAP version of the generated envelope. <code>1.1</code> uses the
     * <code>http://schemas.xmlsoap.org/soap/envelope/</code> namespace and a <code>text/xml</code> Content-Type;
     * <code>1.2</code> uses <code>http://www.w3.org/2003/05/soap-envelope</code> and <code>application/soap+xml</code>.
     * @default 1.1
     * @example 1.2
     */
    @MCAttribute
    public void setVersion(String version) {
        this.version = SoapVersion.parse(version);
    }

    @Override
    protected String getDefaultContentType() {
        return TEXT_XML;
    }
}
