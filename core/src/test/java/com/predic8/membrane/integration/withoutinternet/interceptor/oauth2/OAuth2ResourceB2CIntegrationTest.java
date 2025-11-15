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

package com.predic8.membrane.integration.withoutinternet.interceptor.oauth2;

import com.predic8.membrane.core.interceptor.oauth2.client.b2c.OAuth2ResourceB2CTestSetup;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static com.predic8.membrane.core.http.Request.get;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class OAuth2ResourceB2CIntegrationTest extends OAuth2ResourceB2CTestSetup {

    // this test also implicitly tests concurrency on oauth2resource
    @Test
    void useRefreshTokenOnTokenExpiration() throws Exception {
        mockAuthorizationServer.expiresIn = 1;

        var excCallResource = browser.apply(get(tc.getClientAddress() + "/init"));
        var body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));

        Set<String> accessTokens = new HashSet<>();
        runInParallel((cdl) -> parallelTestWorker(cdl, accessTokens), tc.limit);
        synchronized (accessTokens) {
            assertEquals(accessTokens.size(), tc.limit);
        }
    }

    private void runInParallel(Consumer<CountDownLatch> job, int threadCount) {
        List<Thread> threadList = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            threadList.add(new Thread(() -> job.accept(cdl)));
        }
        threadList.forEach(Thread::start);
        threadList.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void parallelTestWorker(CountDownLatch cdl, Set<String> accessTokens) {
        try {
            cdl.countDown();
            cdl.await();

            String uuid = UUID.randomUUID().toString();
            var excCallResource2 = browser.apply(get(tc.getClientAddress() + "/api/" + uuid));

            var body = om.readValue(excCallResource2.getResponse().getBodyAsStringDecoded(), Map.class);
            String path = (String) body.get("path");
            assertEquals("/api/" + uuid, path);
            synchronized (accessTokens) {
                accessTokens.add((String) body.get("accessToken"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
