package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import groovy.text.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_SOAP;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.templating.SoapBodyTemplateInterceptor.SoapVersion.SOAP_11;

/**
 * @description Renders a SOAP body for legacy intergration
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name="soapBody", mixed = true)
public class SoapBodyTemplateInterceptor extends TemplateInterceptor {

    // Move sometime to a better place and reuse
    public enum SoapVersion {
        SOAP_11("1.1"), SOAP_12("1.2");

        private final String value;

        SoapVersion(String value) {
            this.value = value;
        }


        @Override
        public String toString() {
            return value;
        }
    }

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
    protected String fillTemplate(Exchange exc, Message msg, Flow flow) throws TemplateExecutionException {
        return switch (version) {
            case SOAP_11 -> SOAP11_PREFIX + super.fillTemplate(exc, msg, flow) + SOAP11_POSTFIX;
            case SOAP_12 -> SOAP12_PREFIX + super.fillTemplate(exc, msg, flow) + SOAP12_POSTFIX;
        };
    }

    @Override
    public String getContentType() {
        return switch (version) {
            case SOAP_11 -> TEXT_XML;
            case SOAP_12 -> APPLICATION_SOAP;
        };
    }

    public String getVersion() {
        return version.toString();
    }

    @MCAttribute
    public void setVersion(String version) {
        this.version = switch (version) {
            case "1.1","11" -> SoapVersion.SOAP_11;
            case "1.2","12" -> SoapVersion.SOAP_12;
            default -> throw new ConfigurationException("SOAP version %s is not supported by soapBody.".formatted(version));
        };
    }
}
