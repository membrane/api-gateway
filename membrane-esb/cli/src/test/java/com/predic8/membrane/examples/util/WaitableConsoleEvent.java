package com.predic8.membrane.examples.util;

import java.util.concurrent.TimeoutException;

import com.google.common.base.Predicate;
import com.predic8.membrane.examples.AbstractConsoleWatcher;
import com.predic8.membrane.examples.ScriptLauncher;

/**
 * Watches the console output until the predicate turns true.
 */
public class WaitableConsoleEvent {
	private AbstractConsoleWatcher watcher;
	private boolean event;
	
	public WaitableConsoleEvent(final ScriptLauncher scriptLauncher, final Predicate<String> predicate) {
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
