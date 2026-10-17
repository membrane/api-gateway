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
package com.predic8.membrane.core.util;

import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toCollection;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThreadTestUtil {

    private ThreadTestUtil() {
    }

    /**
     * Threads of unnamed java.util.Timers, such as TimerManager's, are named "Timer-N". Returns a mutable set, so a
     * test can remove the threads that were alive before.
     */
    public static Set<Thread> liveTimerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith("Timer-"))
                .collect(toCollection(HashSet::new));
    }

    /** Fails if a timer thread that was not in {@code before} is still alive after a grace period. */
    public static void assertNoTimerThreadStartedSince(Set<Thread> before) throws InterruptedException {
        final var started = liveTimerThreads();
        started.removeAll(before);
        for (final var thread : started)
            thread.join(5_000); // Timer.cancel() ends the thread asynchronously
        started.removeIf(thread -> !thread.isAlive());
        assertEquals(Set.of(), started);
    }
}
