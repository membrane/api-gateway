package com.predic8.membrane.examples;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Starts a shell script (Windows batch file or Linux shell script) and
 * later kills it.
 * 
 * **********************************************************************
 * You might have to run "powershell Set-ExecutionPolicy RemoteSigned" as
 * administrator before using this class.
 * **********************************************************************
 * 
 * 
 * Note that ProcessStuff is not synchronized, only ProcessStuff.watchers.
 */
public class ScriptLauncher {
	
	private final File exampleDir;
	private ProcessStuff stuff;
	
	public ScriptLauncher(File exampleDir) {
		this.exampleDir = exampleDir;
		if (!exampleDir.exists())
			throw new RuntimeException("Example dir " + exampleDir.getAbsolutePath() + " does not exist.");
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
		
		public int waitFor() throws InterruptedException {
			int res = p.waitFor();
			inputReader.interrupt();
			errorReader.interrupt();
			return res;
		}
	}
	
	public ScriptLauncher startExecutable(String line, AbstractConsoleWatcher... consoleWatchers) throws IOException, InterruptedException {
		return startScriptInternal("executable", line, consoleWatchers);
	}
	
	public ScriptLauncher startScript(String scriptName, AbstractConsoleWatcher... consoleWatchers) throws IOException, InterruptedException {
		return startScriptInternal(scriptName, (isWindows() ? "cmd /c " + scriptName + ".bat" : "bash " + scriptName + ".sh"), consoleWatchers);
	}

	public ScriptLauncher startScriptInternal(String id, String startCommand, AbstractConsoleWatcher... consoleWatchers) throws IOException, InterruptedException {
		ArrayList<String> command = new ArrayList<String>();
		Charset charset;
		if (isWindows()) {
			File ps1 = new File(exampleDir, id + ".ps1");
			FileWriter fw = new FileWriter(ps1);
			fw.write("\"\" + [System.Diagnostics.Process]::GetCurrentProcess().Id > \""+id+".pid\"\r\n" +
					startCommand+"\r\n"+
					"exit $LASTEXITCODE");
			fw.close();
			charset = Charset.forName("UTF-16"); // powershell writes UTF-16 files by default 
			
			command.add("powershell");
			command.add(ps1.getAbsolutePath());
		} else {
			File ps1 = new File(exampleDir, id + "_launcher.sh");
			FileWriter fw = new FileWriter(ps1);
			fw.write("#!/bin/bash\n"+
					"echo $$ > \""+id+".pid\"\n" +
					startCommand);
			fw.close();
			ps1.setExecutable(true);
			charset = Charset.defaultCharset(); // on Linux, the file is probably using some 8-bit charset

			command.add("setsid"); // start new process group so we can kill it at once
			command.add(ps1.getAbsolutePath());
		}
		
		ProcessBuilder pb = new ProcessBuilder(command).directory(exampleDir);
		pb.environment().remove("MEMBRANE_HOME");
		//pb.redirectError(ProcessBuilder.Redirect.PIPE).redirectOutput(Redirect.PIPE).redirectInput(Redirect.PIPE);
		final Process p = pb.start();
		
		p.getOutputStream().close();
		
		ProcessStuff ps = new ProcessStuff(p);
		stuff = ps;
		
		for (AbstractConsoleWatcher acw : consoleWatchers)
			ps.watchers.add(acw);
		
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
			File f = new File(exampleDir, id + ".pid");
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
		
		Thread.sleep(5000); // wait for router to start

		return this;
	}

	private boolean isWindows() {
		return System.getProperty("os.name").contains("Windows");
	}
	
	public ScriptLauncher addConsoleWatcher(AbstractConsoleWatcher watcher) {
		synchronized(stuff.watchers) {
			stuff.watchers.add(watcher);
		}
		return this;
	}

	public ScriptLauncher removeConsoleWatcher(AbstractConsoleWatcher watcher) {
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

		// wait for killer to complete
		Process killer = pb.start();
		ProcessStuff killerStuff = new ProcessStuff(killer);
		killerStuff.startOutputWatchers();
		killerStuff.waitFor();
		
		// wait for membrane to terminate
		ps.waitFor();
	}
	
	public int waitFor(long timeout) {
		long start = System.currentTimeMillis();
		while (true) {
			try {
				return stuff.p.exitValue();
			} catch (IllegalThreadStateException e) {
				// continue waiting
			}
			long left = timeout - (System.currentTimeMillis() - start);
			if (left <= 0)
				throw new RuntimeException(new TimeoutException());
			try {
				wait(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
