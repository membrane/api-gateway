/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples;

import com.predic8.membrane.errorhandling.OpenAPIConfigErrorTest;
import com.predic8.membrane.examples.config.ProxiesXMLOfflineExampleTest;
import com.predic8.membrane.examples.config.ProxiesXMLSecurityExampleTest;
import com.predic8.membrane.examples.env.ConsistentVersionNumbers;
import com.predic8.membrane.examples.env.JavaLicenseInfoTest;
import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing1StaticExampleTest;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing3ClientExampleTest;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing5MultipleExampleTest;
import com.predic8.membrane.examples.tests.log.AccessLogExampleTest;
import com.predic8.membrane.examples.tests.message_transformation.Json2XmlExampleTest;
import com.predic8.membrane.examples.tests.message_transformation.TransformationUsingJavascriptExampleTest;
import com.predic8.membrane.examples.tests.message_transformation.Xml2JsonExampleTest;
import com.predic8.membrane.examples.tests.oauth2.OAuth2APIExampleTest;
import com.predic8.membrane.examples.tests.oauth2.OAuth2CredentialsExampleTest;
import com.predic8.membrane.examples.tests.oauth2.OAuth2MembraneExampleTest;
import com.predic8.membrane.examples.tests.openapi.OpenAPIRewriteExampleTest;
import com.predic8.membrane.examples.tests.openapi.OpenAPIValidationSimpleExampleTest;
import com.predic8.membrane.examples.tests.openapi.OpenAPIValidationExampleTest;
import com.predic8.membrane.examples.tests.opentelemetry.OpenTelemetryExampleTest;
import com.predic8.membrane.examples.tests.template.json.JsonTemplateExampleTest;
import com.predic8.membrane.examples.tests.template.text.TextTemplateExampleTest;
import com.predic8.membrane.examples.tests.template.xml.XMLTemplateExampleTest;
import com.predic8.membrane.examples.tests.validation.JSONSchemaValidationExampleTest;
import com.predic8.membrane.examples.tests.validation.SOAPProxyValidationExampleTest;
import com.predic8.membrane.examples.tests.validation.XMLValidationExampleTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        // Not examples
        ConsistentVersionNumbers.class,
        JavaLicenseInfoTest.class,
        OpenAPIConfigErrorTest.class,

        // Proxy
        CBRXPathExampleTest.class,
        ProxiesXMLOfflineExampleTest.class,
        ProxiesXMLSecurityExampleTest.class,

       // Scripting
        IfInterceptorExampleTest.class,
        GroovyExampleTest.class,
        JavascriptExampleTest.class,

        // Load Balancing
        Loadbalancing1StaticExampleTest.class,
        Loadbalancing3ClientExampleTest.class,
        Loadbalancing5MultipleExampleTest.class,

        // Validation
        JSONSchemaValidationExampleTest.class,
        XMLValidationExampleTest.class,
        SampleSoapServiceExampleTest.class,
        SOAPProxyValidationExampleTest.class,

        // Logging
        AccessLogExampleTest.class,

        // Transformation
        Xml2JsonExampleTest.class,
        Json2XmlExampleTest.class,
        TransformationUsingJavascriptExampleTest.class,

        // OAuth2
        OAuth2APIExampleTest.class,
        OAuth2MembraneExampleTest.class,
        OAuth2CredentialsExampleTest.class,

        // OpenAPI
        OpenAPIValidationSimpleExampleTest.class,
        OpenAPIValidationExampleTest.class,
        OpenTelemetryExampleTest.class,
        OpenAPIRewriteExampleTest.class,

        // Template
        TextTemplateExampleTest.class,
        JsonTemplateExampleTest.class,

        // Security
        JsonProtectionExampleTest.class,
        APIKeyExampleTest.class,
        APIKeyRBACExampleTest.class,
        APIKeyWithOpenAPIExampleTest.class,
        XMLTemplateExampleTest.class,
        //DefaultConfigAdminConsoleTest.class*/

        // XML
        StaxInterceptorExampleTest.class,

        // SOAP
        AddSoapHeaderExampleTest.class,

        InternalProxyExampleTest.class
})
class ExampleTestsWithoutInternet {
}
