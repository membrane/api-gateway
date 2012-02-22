package com.predic8.membrane.examples;

/**
 * Watches what happens on a client process's console.
 */
public abstract class AbstractConsoleWatcher {
	/**
	 * @param error whether the line was printed on stdout or stderr
	 * @param line the line
	 */
	public abstract void outputLine(boolean error, String line);

}
