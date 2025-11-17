package com.predic8.membrane.integration;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        IntegrationTestsWithoutInternet.class,
        IntegrationTestsWithInternet.class
})
public class IntegrationTests {
}
