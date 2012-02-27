package com.predic8.membrane.examples.util;

import com.predic8.membrane.examples.AbstractConsoleWatcher;

public class ConsoleLogger extends AbstractConsoleWatcher {

	@Override
	public void outputLine(boolean error, String line) {
		if (error)
			System.err.println(line);
		else
			System.out.println(line);
	}

}
