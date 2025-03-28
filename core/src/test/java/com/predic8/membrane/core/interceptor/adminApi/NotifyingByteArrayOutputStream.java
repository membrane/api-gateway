package com.predic8.membrane.core.interceptor.adminApi;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class NotifyingByteArrayOutputStream extends ByteArrayOutputStream {
    CountDownLatch countDownLatch = new CountDownLatch(1);

    @Override
    public void write(int b) {
        super.write(b);
        countDownLatch.countDown();
    }

    @Override
    public void write(byte[] b, int off, int len) {
        super.write(b, off, len);
        countDownLatch.countDown();
    }

    public void waitForData() throws InterruptedException {
        countDownLatch.await();
    }

}
