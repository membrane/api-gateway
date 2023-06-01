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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static com.predic8.membrane.core.util.OSUtil.isWindows;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;

/**
 * Starts a shell script (Windows batch file or Linux shell script) or
 * executable and later kills it.
 * **********************************************************************
 * You might have to run "powershell Set-ExecutionPolicy RemoteSigned" as
 * administrator before using this class.
 * **********************************************************************
 * <p>
 * Note that ProcessStuff is not synchronized, only ProcessStuff.watchers.
 */
public class Process2 implements AutoCloseable {

	@Override
	public void close() {
		killScript();
	}

	public static class Builder {
		private File baseDir;
		private String id;
		private String line;
		private String waitAfterStartFor;
		private String parameters = "";
		private final ArrayList<ConsoleWatcher> watchers = new ArrayList<>();

		public Builder() {}

		public Builder in(File baseDir) {
			this.baseDir = baseDir;
			return this;
		}

		public Builder parameters(String parameters) {
			this.parameters = parameters;
			return this;
		}

		public Builder executable(String line) {
			if (id != null)
				throw new IllegalStateException("executable or script is already set.");
			id = "executable";
			this.line = line;
			return this;
		}

		public Builder script(String script) {
			if (id != null)
				throw new IllegalStateException("executable or script is already set.");
			id = script;
			line = isWindows() ? "cmd /c " + script + ".bat" : "bash " + script + ".sh";
			return this;
		}

		public Builder withWatcher(ConsoleWatcher watcher) {
			watchers.add(watcher);
			return this;
		}

		public Builder waitAfterStartFor(String s) {
			waitAfterStartFor = s;
			return this;
		}

		public Builder waitForMembrane() {
			waitAfterStartFor("listening at ");
			return this;
		}

		public Process2 start() throws IOException, InterruptedException {
			if (baseDir == null)
				throw new IllegalStateException("baseDir not set");
			if (id == null)
				throw new IllegalStateException("id not set");
			if (line == null)
				throw new IllegalStateException("line not set");

			line += " " + parameters;
			System.out.println("Starting: " + line);
			return new Process2(baseDir, id, line, watchers, waitAfterStartFor);
		}
	}

	private static final class OutputWatcher extends Thread {
		private final Process2 ps;
		private final InputStream is;
		private final boolean error;

		private OutputWatcher(Process2 ps, InputStream is, boolean error) {
			this.ps = ps;
			this.is = is;
			this.error = error;
		}

		@Override
		public void run() {
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				while (!interrupted()) {
					String l = br.readLine();
					if (l == null)
						break;
					ArrayList<ConsoleWatcher> watchers;
					synchronized(ps.watchers) {
						watchers = new ArrayList<>(ps.watchers);
					}
					for (ConsoleWatcher watcher : watchers)
						watcher.outputLine(error, l);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private final Process p;
	private Thread inputReader, errorReader;
	private final List<ConsoleWatcher> watchers = new ArrayList<>();

	private Process2(File exampleDir, String id, String startCommand, List<ConsoleWatcher> consoleWatchers, String waitAfterStartFor) throws IOException, InterruptedException {

		System.out.println("exampleDir = " + exampleDir + ", id = " + id + ", startCommand = " + startCommand + ", consoleWatchers = " + consoleWatchers + ", waitAfterStartFor = " + waitAfterStartFor);

		if (!exampleDir.exists())
			throw new RuntimeException("Example dir " + exampleDir.getAbsolutePath() + " does not exist.");

		p = getProcessBuilder(exampleDir, startCommand).start();
		p.getOutputStream().close();

		consoleWatchers.add((error, line) -> System.out.println(line));

		watchers.addAll(consoleWatchers);

		SubstringWaitableConsoleEvent afterStartWaiter = null;
		if (waitAfterStartFor != null)
			afterStartWaiter = new SubstringWaitableConsoleEvent(this, waitAfterStartFor);

		startOutputWatchers();

		if (afterStartWaiter != null) {
			try {
				afterStartWaiter.waitFor(10000);
			} catch (TimeoutException e) {
				killScript();
				throw new RuntimeException(e);
			}
		}
		sleep(100);
	}

	private Stream<ProcessHandle> getChildrenRecursively(ProcessHandle p) {
		return Stream.concat(
				Stream.of(p),
				p.children().flatMap(this::getChildrenRecursively)
		);
	}

	private ProcessBuilder getProcessBuilder(File exampleDir, String startCommand) {
		ProcessBuilder pb = new ProcessBuilder(startCommand.split(" "));
		pb.directory(exampleDir);
		pb.environment().remove("MEMBRANE_HOME");
		if (!isWindows()) {
			pb.environment().put("PATH", System.getProperty("java.home") + "/bin:" + System.getenv("PATH"));
			pb.environment().put("JAVA_HOME", System.getProperty("java.home"));
		}
		//pb.redirectError(ProcessBuilder.Redirect.PIPE).redirectOutput(Redirect.PIPE).redirectInput(Redirect.PIPE);
		return pb;
	}

	public void addConsoleWatcher(ConsoleWatcher watcher) {
		synchronized(watchers) {
			watchers.add(watcher);
		}
	}

	public void removeConsoleWatcher(ConsoleWatcher watcher) {
		synchronized(watchers) {
			watchers.remove(watcher);
		}
	}

	public void killScript() {
		if (inputReader != null) inputReader.interrupt();
		if (errorReader != null) errorReader.interrupt();
		getChildrenRecursively(p.toHandle()).forEach(ProcessHandle::destroyForcibly);
	}

	private static int waitForExit(Process p, long timeout) {
		long start = System.currentTimeMillis();
		while (p.isAlive()) {
			if (getTimeLeft(timeout, start) <= 0)
				throw new RuntimeException(new TimeoutException());
			try {
				//noinspection BusyWait
				sleep(200);
			} catch (InterruptedException e) {
				currentThread().interrupt();
			}
		}
		return p.exitValue();
	}

	private static long getTimeLeft(long timeout, long start) {
		return timeout - (System.currentTimeMillis() - start);
	}

	public int waitForExit(long timeout) {
		return waitForExit(p, timeout);
	}

	public void startOutputWatchers() {
		inputReader = new OutputWatcher(this, p.getInputStream(), false);
		inputReader.start();
		errorReader = new OutputWatcher(this, p.getErrorStream(), true);
		errorReader.start();
	}

}
