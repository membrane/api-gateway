package com.predic8.membrane.examples.tests.ssl;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.test.AssertUtils.*;

public class SSLServerApiWithTlsPemTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "ssl/api-with-tls-pem";
    }

    @Test
    void test() throws Exception {
        replaceInFile2("proxies.xml", "443", "3023");

        try(Process2 ignored = startServiceProxyScript()) {
            trustAnyHTTPSServer(3023);
            assertContains("success", getAndAssert200("https://localhost:3023"));
        }
    }

}
