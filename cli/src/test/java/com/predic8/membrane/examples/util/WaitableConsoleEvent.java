/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.util;

import java.util.concurrent.TimeoutException;

import com.google.common.base.Predicate;
import com.predic8.membrane.examples.AbstractConsoleWatcher;
import com.predic8.membrane.examples.Process2;

/**
 * Watches the console output until the predicate turns true.
 */
public class WaitableConsoleEvent {
	private AbstractConsoleWatcher watcher;
	private boolean event;
	
	public WaitableConsoleEvent(final Process2 scriptLauncher, final Predicate<String> predicate) {
		watcher = new AbstractConsoleWatcher() {
			@Override
			public void outputLine(boolean error, String line) {
				if (predicate.apply(line)) {
					synchronized (WaitableConsoleEvent.this) {
						event = true;
						scriptLauncher.removeConsoleWatcher(watcher);
						WaitableConsoleEvent.this.notifyAll();
					}
				}
			}
		};
		scriptLauncher.addConsoleWatcher(watcher);
	}
	
	public synchronized boolean occurred() {
		return event;
	}
	
	public synchronized void waitFor(long timeout) {
		long start = System.currentTimeMillis();
		while (true) {
			if (event)
				return;
			long left = timeout - (System.currentTimeMillis() - start);
			if (left <= 0)
				throw new RuntimeException(new TimeoutException());
			try {
				wait(left);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
