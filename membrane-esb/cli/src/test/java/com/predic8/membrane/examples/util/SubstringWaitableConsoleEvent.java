package com.predic8.membrane.examples.util;

import com.google.common.base.Predicate;
import com.predic8.membrane.examples.Process2;

/**
 * Watches the console until "substring" is found.
 */
public class SubstringWaitableConsoleEvent extends WaitableConsoleEvent {
	
	public SubstringWaitableConsoleEvent(Process2 launcher, final String substring) {
		super(launcher, new Predicate<String>() {
			@Override
			public boolean apply(String line) {
				return line.contains(substring);
			}
		});
	}

}
