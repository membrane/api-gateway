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
import com.predic8.membrane.examples.config.DefaultConfigAdminConsoleTest;
import com.predic8.membrane.examples.config.ProxiesXMLOfflineTest;
import com.predic8.membrane.examples.config.ProxiesXMLSecurityTest;
import com.predic8.membrane.examples.env.ConsistentVersionNumbers;
import com.predic8.membrane.examples.env.JavaLicenseInfoTest;
import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing1StaticTest;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing3ClientTest;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing4XmlSessionTest;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing5MultipleTest;
import com.predic8.membrane.examples.tests.oauth2.OAuth2APITest;
import com.predic8.membrane.examples.tests.oauth2.OAuth2CredentialsTest;
import com.predic8.membrane.examples.tests.oauth2.OAuth2MembraneTest;
import com.predic8.membrane.examples.tests.openapi.OpenAPIValidationSimpleTest;
import com.predic8.membrane.examples.tests.openapi.OpenAPIValidationTest;
import com.predic8.membrane.examples.tests.template.json.JsonTemplateTest;
import com.predic8.membrane.examples.tests.template.text.TextTemplateTest;
import com.predic8.membrane.examples.tests.template.xml.XMLTemplateTest;
import com.predic8.membrane.examples.tests.validation.JSONSchemaValidationTest;
import com.predic8.membrane.examples.tests.validation.XMLValidationTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        ConsistentVersionNumbers.class,
        DefaultConfigAdminConsoleTest.class,
        JavaLicenseInfoTest.class,

        CBRTest.class,
        GroovyTest.class,
        JavascriptTest.class,
        Loadbalancing1StaticTest.class,
        Loadbalancing3ClientTest.class,
        Loadbalancing5MultipleTest.class,

        JSONSchemaValidationTest.class,
        XMLValidationTest.class,

        Xml2JsonTest.class,
        Json2XmlTest.class,

        // OAuth2
        OAuth2APITest.class,
        OAuth2MembraneTest.class,
        OAuth2CredentialsTest.class,

        OpenAPIValidationSimpleTest.class,
        OpenAPIValidationTest.class,

        ProxiesXMLOfflineTest.class,
        ProxiesXMLSecurityTest.class,

        // Not an example test. Maybe we can find a better location?
        OpenAPIConfigErrorTest.class,

        TextTemplateTest.class,
        JsonTemplateTest.class,
        XMLTemplateTest.class
})
public class ExampleTestsWithoutInternet {
}
