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

package com.predic8.membrane.examples;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeoutException;

import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;

/**
 * Starts a shell script (Windows batch file or Linux shell script) or
 * executable and later kills it.
 *
 * **********************************************************************
 * You might have to run "powershell Set-ExecutionPolicy RemoteSigned" as
 * administrator before using this class.
 * **********************************************************************
 *
 *
 * Note that ProcessStuff is not synchronized, only ProcessStuff.watchers.
 */
public class Process2 {

	public static class Builder {
		private File baseDir;
		private String id;
		private String line;
		private String waitAfterStartFor;
		private ArrayList<AbstractConsoleWatcher> watchers = new ArrayList<AbstractConsoleWatcher>();

		public Builder() {}

		public Builder in(File baseDir) {
			this.baseDir = baseDir;
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

		public Builder withWatcher(AbstractConsoleWatcher watcher) {
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
					ArrayList<AbstractConsoleWatcher> watchers;
					synchronized(ps.watchers) {
						watchers = new ArrayList<AbstractConsoleWatcher>(ps.watchers);
					}
					for (AbstractConsoleWatcher watcher : watchers)
						watcher.outputLine(error, l);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private class ProcessStuff {
		public final Process p;
		public Integer pid;
		public Thread inputReader, errorReader;
		public final List<AbstractConsoleWatcher> watchers = new ArrayList<AbstractConsoleWatcher>();

		public ProcessStuff(Process p) {
			this.p = p;
		}

		public void startOutputWatchers() {
			inputReader = new OutputWatcher(this, p.getInputStream(), false);
			inputReader.start();
			errorReader = new OutputWatcher(this, p.getErrorStream(), true);
			errorReader.start();
		}

		public int waitFor(long timeout) throws InterruptedException {
			int res = Process2.waitFor(p, timeout);
			inputReader.interrupt();
			errorReader.interrupt();
			return res;
		}
	}

	private ProcessStuff stuff;

	private static Random random = new Random(System.currentTimeMillis());

	private Process2(File exampleDir, String id, String startCommand, List<AbstractConsoleWatcher> consoleWatchers, String waitAfterStartFor) throws IOException, InterruptedException {
		if (!exampleDir.exists())
			throw new RuntimeException("Example dir " + exampleDir.getAbsolutePath() + " does not exist.");

		String pidFile;
		synchronized(random) {
			pidFile = id + "-" + random.nextInt() + ".pid";
		}

		ArrayList<String> command = new ArrayList<String>();
		Charset charset;
		Map<String, String> envVarAdditions = new HashMap<>();

		if (isWindows()) {
			File ps1 = new File(exampleDir, id + ".ps1");
			FileWriter fw = new FileWriter(ps1);
			fw.write("\"\" + [System.Diagnostics.Process]::GetCurrentProcess().Id > \""+pidFile+"\"\r\n" +
					startCommand+"\r\n"+
					"exit $LASTEXITCODE");
			fw.close();
			charset = Charset.forName("UTF-16"); // powershell writes UTF-16 files by default

			command.add("powershell");
			command.add(ps1.getAbsolutePath());
		} else {
			File ps1 = new File(exampleDir, id + "_launcher.sh");
			FileWriter fw = new FileWriter(ps1);
			fw.write("#!/bin/bash\n");
			fw.write("echo $$ > \""+pidFile+"\"\n" + startCommand);
			fw.close();
			ps1.setExecutable(true);
			charset = Charset.defaultCharset(); // on Linux, the file is probably using some 8-bit charset
			command.add("setsid"); // start new process group so we can kill it at once
			command.add(ps1.getAbsolutePath());

			envVarAdditions.put("PATH", System.getProperty("java.home") +"/bin:" + System.getenv("PATH"));
			envVarAdditions.put("JAVA_HOME", System.getProperty("java.home"));
		}

		ProcessBuilder pb = new ProcessBuilder(command).directory(exampleDir);
		pb.environment().remove("MEMBRANE_HOME");
		pb.environment().putAll(envVarAdditions);
		//pb.redirectError(ProcessBuilder.Redirect.PIPE).redirectOutput(Redirect.PIPE).redirectInput(Redirect.PIPE);
		final Process p = pb.start();

		p.getOutputStream().close();

		ProcessStuff ps = new ProcessStuff(p);
		stuff = ps;
		consoleWatchers.add(new AbstractConsoleWatcher() {
			@Override
			public void outputLine(boolean error, String line) {
				System.out.println(line);
			}
		});


		for (AbstractConsoleWatcher acw : consoleWatchers)
			ps.watchers.add(acw);

		SubstringWaitableConsoleEvent afterStartWaiter = null;
		if (waitAfterStartFor != null)
			afterStartWaiter = new SubstringWaitableConsoleEvent(this, waitAfterStartFor);

		ps.startOutputWatchers();

		// now wait for the PID to be written
		for (int i = 0; i < 1001; i++) {
			if (i % 20 == 0) {
				try {
					throw new RuntimeException("Process terminated with exit code " + p.exitValue());
				} catch (IllegalThreadStateException e) {
					// did not terminate yet
				}
			}
			if (i == 1000)
				throw new RuntimeException("could not read PID file");
			Thread.sleep(100);
			File f = new File(exampleDir, pidFile);
			if (!f.exists())
				continue;
			FileInputStream fr = new FileInputStream(f);
			try {
				String line = new BufferedReader(new InputStreamReader(fr, charset)).readLine();
				if (line == null)
					continue;
				stuff.pid = Integer.parseInt(line);
				break;
			} catch (NumberFormatException e) {
				// ignore
			} finally {
				fr.close();
			}
		}

		if (afterStartWaiter != null)
			afterStartWaiter.waitFor(60000);
		Thread.sleep(100);
	}

	public static boolean isWindows() {
		return System.getProperty("os.name").contains("Windows");
	}

	public Process2 addConsoleWatcher(AbstractConsoleWatcher watcher) {
		synchronized(stuff.watchers) {
			stuff.watchers.add(watcher);
		}
		return this;
	}

	public Process2 removeConsoleWatcher(AbstractConsoleWatcher watcher) {
		synchronized(stuff.watchers) {
			stuff.watchers.remove(watcher);
		}
		return this;
	}

	public void killScript() throws InterruptedException, IOException {
		ProcessStuff ps = stuff;

		// start the killer
		ArrayList<String> command = new ArrayList<String>();
		if (isWindows()) {
			command.add("taskkill");
			command.add("/T"); // kill whole subtree
			command.add("/F");
			command.add("/PID");
			command.add(""+ps.pid);
		} else {
			command.add("kill");
			command.add("-TERM");
			command.add("-"+ps.pid);
		}
		ProcessBuilder pb = new ProcessBuilder(command);
		//pb.redirectInput(Redirect.PIPE).redirectError(Redirect.PIPE).redirectOutput(Redirect.PIPE);

		//System.out.println("Killing process " + ps.pid);
		// wait for killer to complete
		Process killer = pb.start();
		ProcessStuff killerStuff = new ProcessStuff(killer);
		//killerStuff.watchers.add(new ConsoleLogger());
		killerStuff.startOutputWatchers();
		killerStuff.waitFor(60000);

		// wait for membrane to terminate
		ps.waitFor(60000);
	}

	private static int waitFor(Process p, long timeout) {
		long start = System.currentTimeMillis();
		while (true) {
			try {
				return p.exitValue();
			} catch (IllegalThreadStateException e) {
				// continue waiting
			}
			long left = timeout - (System.currentTimeMillis() - start);
			if (left <= 0)
				throw new RuntimeException(new TimeoutException());
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public int waitFor(long timeout) {
		return waitFor(stuff.p, timeout);
	}

}
