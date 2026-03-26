package com.predic8.membrane.test;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

public class TestAppender extends AbstractAppender {

    private final List<String> messages = new CopyOnWriteArrayList<>();

    public TestAppender(String name) {
        super(name, null, null, false, null);
    }

    @Override
    public void append(LogEvent event) {
        messages.add(event.getMessage().getFormattedMessage());
    }

    public List<String> getMessages() {
        return messages;
    }

    public boolean contains(String text) {
        return messages.stream().anyMatch(msg -> msg.contains(text));
    }

    public boolean awaitContains(String text, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (contains(text)) {
                return true;
            }
            Thread.sleep(50);
        }

        return false;
    }

    public void awaitContainsOrThrow(String text, Duration timeout) throws InterruptedException, TimeoutException {
        if (!awaitContains(text, timeout)) {
            throw new TimeoutException("Did not find log message containing '" + text + "' within " + timeout +
                    ". Logs: " + messages);
        }
    }
}