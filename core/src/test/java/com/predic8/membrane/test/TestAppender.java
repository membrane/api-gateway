/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

public class TestAppender extends AbstractAppender {

    private final List<String> messages = new CopyOnWriteArrayList<>();
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    public TestAppender(String name) {
        super(name, null, null, false, null);
    }

    /**
     * Runs the action and returns the messages logged meanwhile by any logger (on any thread).
     */
    public static TestAppender captureLog(Callable<?> action) throws Exception {
        final var root = (Logger) LogManager.getRootLogger();
        final var appender = new TestAppender("captureLog");
        appender.start();
        root.addAppender(appender);
        try {
            action.call();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
        return appender;
    }

    @Override
    public void append(LogEvent event) {
        messages.add(event.getMessage().getFormattedMessage());
        events.add(event.toImmutable());
    }

    /**
     * @return the captured events whose formatted message contains {@code text}, e.g. to check their level or
     * whether a throwable was attached
     */
    public List<LogEvent> eventsContaining(String text) {
        return events.stream().filter(event -> event.getMessage().getFormattedMessage().contains(text)).toList();
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