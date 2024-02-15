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
import com.predic8.membrane.examples.config.ProxiesXMLOfflineTest;
import com.predic8.membrane.examples.config.ProxiesXMLSecurityTest;
import com.predic8.membrane.examples.env.ConsistentVersionNumbers;
import com.predic8.membrane.examples.env.JavaLicenseInfoTest;
import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing1StaticTest;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing3ClientTest;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing5MultipleTest;
import com.predic8.membrane.examples.tests.message_transformation.Json2XmlTest;
import com.predic8.membrane.examples.tests.message_transformation.TransformationUsingJavascriptTest;
import com.predic8.membrane.examples.tests.message_transformation.Xml2JsonTest;
import com.predic8.membrane.examples.tests.oauth2.OAuth2APITest;
import com.predic8.membrane.examples.tests.oauth2.OAuth2CredentialsTest;
import com.predic8.membrane.examples.tests.oauth2.OAuth2MembraneTest;
import com.predic8.membrane.examples.tests.openapi.OpenAPIValidationSimpleTest;
import com.predic8.membrane.examples.tests.openapi.OpenAPIValidationTest;
import com.predic8.membrane.examples.tests.opentelemetry.OpenTelemetryInterceptorTest;
import com.predic8.membrane.examples.tests.template.json.JsonTemplateTest;
import com.predic8.membrane.examples.tests.template.text.TextTemplateTest;
import com.predic8.membrane.examples.tests.template.xml.XMLTemplateTest;
import com.predic8.membrane.examples.tests.validation.JSONSchemaValidationTest;
import com.predic8.membrane.examples.tests.validation.XMLValidationTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        // Not examples
        ConsistentVersionNumbers.class,
        JavaLicenseInfoTest.class,
        OpenAPIConfigErrorTest.class,

        // Proxy
        CBRTest.class,
        ProxiesXMLOfflineTest.class,
        ProxiesXMLSecurityTest.class,

        // Scripting
        GroovyTest.class,
        JavascriptTest.class,

        // Load Balancing
        Loadbalancing1StaticTest.class,
        Loadbalancing3ClientTest.class,
        Loadbalancing5MultipleTest.class,

        // Validation
        JSONSchemaValidationTest.class,
        XMLValidationTest.class,
        SampleSoapServiceTest.class,

        // Transformation
        Xml2JsonTest.class,
        Json2XmlTest.class,
        TransformationUsingJavascriptTest.class,

        // OAuth2
        OAuth2APITest.class,
        OAuth2MembraneTest.class,
        OAuth2CredentialsTest.class,

        // OpenAPI
        OpenAPIValidationSimpleTest.class,
        OpenAPIValidationTest.class,
        OpenTelemetryInterceptorTest.class,

        // Template
        TextTemplateTest.class,
        JsonTemplateTest.class,
        XMLTemplateTest.class,

        // Security
        JsonProtectionTest.class,
        APIKeyTest.class

        //DefaultConfigAdminConsoleTest.class
})
public class ExampleTestsWithoutInternet {
}
