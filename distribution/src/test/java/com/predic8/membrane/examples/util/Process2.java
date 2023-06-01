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
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static com.predic8.membrane.core.util.OSUtil.isWindows;
import static java.lang.String.format;
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
	public void close() throws Exception {
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

	private final class OutputWatcher extends Thread {
		private final ProcessStuff ps;
		private final InputStream is;
		private final boolean error;

		private OutputWatcher(ProcessStuff ps, InputStream is, boolean error) {
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

	private class ProcessStuff {
		public final Process p;
		public Thread inputReader, errorReader;
		public final List<ConsoleWatcher> watchers = new ArrayList<>();

		public ProcessStuff(Process p) {
			this.p = p;
		}

		public void startOutputWatchers() {
			inputReader = new OutputWatcher(this, p.getInputStream(), false);
			inputReader.start();
			errorReader = new OutputWatcher(this, p.getErrorStream(), true);
			errorReader.start();
		}
	}

	private final ProcessStuff stuff;

	private static final Random random = new Random(System.currentTimeMillis());

	private Process2(File exampleDir, String id, String startCommand, List<ConsoleWatcher> consoleWatchers, String waitAfterStartFor) throws IOException, InterruptedException {

		System.out.println("exampleDir = " + exampleDir + ", id = " + id + ", startCommand = " + startCommand + ", consoleWatchers = " + consoleWatchers + ", waitAfterStartFor = " + waitAfterStartFor);

		if (!exampleDir.exists())
			throw new RuntimeException("Example dir " + exampleDir.getAbsolutePath() + " does not exist.");

		String pidFile = getPidFilename(id);

		Map<String, String> envVarAdditions = new HashMap<>();

		final Process p = getProcessBuilder(exampleDir, id, startCommand, pidFile, envVarAdditions).start();

		p.getOutputStream().close();

		ProcessStuff ps = new ProcessStuff(p);
		stuff = ps;
		consoleWatchers.add((error, line) -> System.out.println(line));

		ps.watchers.addAll(consoleWatchers);

		SubstringWaitableConsoleEvent afterStartWaiter = null;
		if (waitAfterStartFor != null)
			afterStartWaiter = new SubstringWaitableConsoleEvent(this, waitAfterStartFor);

		ps.startOutputWatchers();

		if (afterStartWaiter != null)
			afterStartWaiter.waitFor(10000);
		sleep(100);
	}

	private Stream<ProcessHandle> getChildrenRecursively(ProcessHandle p) {
		return Stream.concat(
				Stream.of(p),
				p.children().flatMap(this::getChildrenRecursively)
		);
	}

	private ProcessBuilder getProcessBuilder(File exampleDir, String id, String startCommand, String pidFile, Map<String, String> envVarAdditions) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(getStartCommand(exampleDir, id, startCommand, pidFile, envVarAdditions)).directory(exampleDir);
		pb.environment().remove("MEMBRANE_HOME");
		pb.environment().putAll(envVarAdditions);
		//pb.redirectError(ProcessBuilder.Redirect.PIPE).redirectOutput(Redirect.PIPE).redirectInput(Redirect.PIPE);
		return pb;
	}

	private static String getPidFilename(String id) {
		synchronized(random) {
			return id + "-" + random.nextInt() + ".pid";
		}
	}

//	private static String getOutputFilename(String id) {
//		synchronized(random) {
//			return id + "-" + random.nextInt() + ".txt";
//		}
//	}
//
//	private Integer waitForPIDtoBeWritten(File exampleDir, String pidFile, Process p) throws InterruptedException, IOException {
//		for (int i = 0; i < 1001; i++) {
//			if (i % 20 == 0) {
//				throwIfTerminated(p);
//			}
//			sleep(100);
//			File f = new File(exampleDir, pidFile);
//			if (!f.exists())
//				continue;
//			try (FileInputStream fr = new FileInputStream(f)) {
//				String line = new BufferedReader(new InputStreamReader(fr, getCharset())).readLine();
//				if (line == null)
//					continue;
//				return Integer.parseInt(line);
//			} catch (NumberFormatException e) {
//				// ignore
//			}
//		}
//		throw new RuntimeException("could not read PID file");
//	}
//
//	private void throwIfTerminated(Process p) {
//		try {
//			throw new RuntimeException("Process terminated with exit code " + p.exitValue());
//		} catch (IllegalThreadStateException e) {
//			// did not terminate yet
//		}
//	}
//
//	private static Charset getCharset() {
//		if (isWindows()) {
//			return UTF_16; // powershell writes UTF-16 files by default
//		} else {
//			return defaultCharset(); // on Linux, the file is probably using some 8-bit charset
//		}
//	}

	// TODO simplify
	private ArrayList<String> getStartCommand(File exampleDir, String id, String startCommand, String pidFile, Map<String, String> envVarAdditions) throws IOException {
		ArrayList<String> command = new ArrayList<>();
		if (isWindows()) {
			File ps1 = new File(exampleDir, id + ".ps1");
			FileWriter fw = new FileWriter(ps1);
			fw.write(createStartCommand(startCommand, pidFile));
			fw.close();
			command.add("powershell");
			command.add(ps1.getAbsolutePath());
		} else {
			// Linux and Mac OS
			File ps1 = new File(exampleDir, id + "_launcher.sh");
//			var stdOutPipe = getOutputFilename(id);
			var launcherScript = """
                    #!/bin/bash
                    %s
                    """.formatted(startCommand); //, stdOutPipe, pidFile, stdOutPipe);
			FileWriter fw = new FileWriter(ps1);
			fw.write(launcherScript);
			fw.close();

			//noinspection ResultOfMethodCallIgnored
			ps1.setExecutable(true);
			command.add("setsid"); // start new process group so we can kill it at once
			command.add(ps1.getAbsolutePath());

			envVarAdditions.put("PATH", System.getProperty("java.home") + "/bin:" + System.getenv("PATH"));
			envVarAdditions.put("JAVA_HOME", System.getProperty("java.home"));
		}
		return command;
	}

	private String createStartCommand(String startCommand, String pidFile) {
		return format("\"\" + [System.Diagnostics.Process]::GetCurrentProcess().Id > \"%s\"\r\n%s\r\nexit $LASTEXITCODE", pidFile, startCommand);
	}

	public void addConsoleWatcher(ConsoleWatcher watcher) {
		synchronized(stuff.watchers) {
			stuff.watchers.add(watcher);
		}
	}

	public void removeConsoleWatcher(ConsoleWatcher watcher) {
		synchronized(stuff.watchers) {
			stuff.watchers.remove(watcher);
		}
	}

	public void killScript() {
		getChildrenRecursively(stuff.p.toHandle()).forEach(ProcessHandle::destroyForcibly);
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
		return waitForExit(stuff.p, timeout);
	}

}
