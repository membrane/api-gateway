/* Copyright 2016 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.authentication.session;

import org.junit.Test;
import org.springframework.util.Assert;

/**
 * Created by Tobias on 12/16/2016.
 */
public class AccountBlockerTest {
    @Test
    public void run() throws InterruptedException {
        AccountBlocker ab = new AccountBlocker();
        ab.setBlockFor(1000);
        ab.setAfterFailedLoginsWithin(1000);
        ab.setAfterFailedLogins(10);

        for (int i = 1; i <= 10 ; i++) {
            Assert.isTrue(!ab.isBlocked("foo"), "login should not be blocked in loop " + i);
            ab.fail("foo");
        }
        Assert.isTrue(ab.isBlocked("foo"));

        Thread.sleep(2000);

        for (int i = 1; i <= 10 ; i++) {
            Assert.isTrue(!ab.isBlocked("foo"), "login should not be blocked in loop " + i);
            ab.fail("foo");
        }
        Assert.isTrue(ab.isBlocked("foo"));

    }


}