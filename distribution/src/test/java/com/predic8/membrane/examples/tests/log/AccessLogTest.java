package com.predic8.membrane.examples.tests.log;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.WaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;

public class AccessLogTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "logging/access";
    }

    @Test
    void testExample() throws Exception {
        try (var process = startServiceProxyScript()) {
            var console = new WaitableConsoleEvent(process, p -> p.equals("127.0.0.1 \"GET / HTTP/1.1\" 200 0 - application/json"));
            getAndAssert200("http://localhost:2000");
            assertTrue(console.occurred());
        }
    }
}
