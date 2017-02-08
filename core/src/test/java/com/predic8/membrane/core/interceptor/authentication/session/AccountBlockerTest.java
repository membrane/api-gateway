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