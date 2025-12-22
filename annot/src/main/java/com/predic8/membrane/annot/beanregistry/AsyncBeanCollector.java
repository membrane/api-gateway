package com.predic8.membrane.annot.beanregistry;

import javax.annotation.concurrent.GuardedBy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Thread-safe, asynchronous wrapper for {@link BeanCollector}.
 */
public class AsyncBeanCollector implements BeanCollector {

    private final BlockingQueue<ChangeEvent> changeEvents = new LinkedBlockingDeque<>();
    private final BeanCollector delegate;

    @GuardedBy("this")
    Thread t;

    public AsyncBeanCollector(BeanCollector delegate) {
        this.delegate = delegate;
    }

    @Override
    public void handle(ChangeEvent changeEvent, boolean isLast) {
        changeEvents.add(changeEvent);
    }

    @Override
    public synchronized void start() {
        if (t != null)
            return;
        t = Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    ChangeEvent changeEvent = changeEvents.take();
                    delegate.handle(changeEvent, changeEvents.isEmpty());
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

}
