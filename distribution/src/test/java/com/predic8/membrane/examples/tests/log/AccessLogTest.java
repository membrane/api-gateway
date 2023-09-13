package com.predic8.membrane.examples.tests.log;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;

public class AccessLogTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "logging/access";
    }

    @Test
    void testExample() throws Exception {
        try (var ignore = startServiceProxyScript()) {
            getAndAssert200("http://localhost:2000");
        }

        assertContains("", readFile("access.log"));
    }
}
