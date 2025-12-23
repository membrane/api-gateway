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
