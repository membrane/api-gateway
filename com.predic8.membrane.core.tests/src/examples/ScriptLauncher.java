package examples;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.After;
import org.junit.Before;

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
	
	private final String name;
	private File projectHome, unzipDir, membraneHome, exampleDir;
	
	
	public ScriptLauncher(String name) {
		this.name = name;
	}
	
	@Before
	public void init() throws IOException, InterruptedException {
		projectHome = new File("../com.predic8.membrane.core").getCanonicalFile();
		if (!projectHome.exists())
			throw new RuntimeException("membraneHome " + projectHome.getName() + " does not exist.");
		
		File zip = null;
		File dist = new File(projectHome, "dist");
		if (dist.exists()) {
			File[] files = dist.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith("membrane-esb") && name.endsWith(".zip");
				}
			});
			if (files.length > 1)
				throw new RuntimeException("found more than one membrane-esb*.zip");
			if (files.length == 1)
				zip = files[0];
		}
		
		if (zip == null)
			throw new RuntimeException("TODO: calling 'ant dist-router' automatically is not implemented.");

		unzipDir = new File(projectHome, "examples-automatic");
		if (unzipDir.exists()) {
			recursiveDelete(unzipDir);
			Thread.sleep(1000);
		}
		if (!unzipDir.mkdir())
			throw new RuntimeException("Could not mkdir " + unzipDir.getAbsolutePath());

		System.out.println("unzipping router distribution...");
		unzip(zip, unzipDir);
		
		membraneHome = unzipDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith("membrane-esb");
			}
		})[0];
		
		exampleDir = new File(membraneHome, "examples/" + name);
		System.out.println("running test...");
	}

	@After
	public void done() {
		System.out.println("cleaning up...");
		recursiveDelete(unzipDir);
		System.out.println("done.");
	}

	private void recursiveDelete(File file) {
		if (file.isDirectory())
			for (File child : file.listFiles())
				recursiveDelete(child);
		if (!file.delete())
			throw new RuntimeException("could not delete " + file.getAbsolutePath());
	}

	private HashMap<Process, ProcessStuff> pids = new HashMap<Process, ProcessStuff>();
	
	private final class OutputWatcher extends Thread {
		private final ProcessStuff ps;
		private final Process p;
		private final InputStream is;
		private final boolean error;

		private OutputWatcher(ProcessStuff ps, Process p, InputStream is, boolean error) {
			this.ps = ps;
			this.p = p;
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
					synchronized(ps.watchers) {
						for (AbstractConsoleWatcher watcher : ps.watchers)
							watcher.outputLine(error, l);
					}
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
			inputReader = new OutputWatcher(this, p, p.getInputStream(), false);
			inputReader.start();
			errorReader = new OutputWatcher(this, p, p.getErrorStream(), true);
			errorReader.start();
		}
		
		public int waitFor() throws InterruptedException {
			int res = p.waitFor();
			inputReader.interrupt();
			errorReader.interrupt();
			return res;
		}
	}
	
	protected Process startScript(String scriptName, AbstractConsoleWatcher... consoleWatchers) throws IOException, InterruptedException {
		if (!exampleDir.exists())
			throw new RuntimeException("Example dir " + exampleDir.getAbsolutePath() + " does not exist.");
		
		ArrayList<String> command = new ArrayList<String>();
		Charset charset;
		if (System.getProperty("os.name").contains("Windows")) {
			File ps1 = new File(exampleDir, scriptName + ".ps1");
			FileWriter fw = new FileWriter(ps1);
			fw.write("\"\" + [System.Diagnostics.Process]::GetCurrentProcess().Id > \""+scriptName+".pid\"\r\n" +
					"cmd /c "+scriptName+".bat");
			fw.close();
			charset = Charset.forName("UTF-16"); // powershell writes UTF-16 files by default 
			
			command.add("powershell");
			command.add(ps1.getAbsolutePath());
		} else {
			File ps1 = new File(exampleDir, scriptName + "_launcher.sh");
			FileWriter fw = new FileWriter(ps1);
			fw.write("#!/bin/bash\n"+
					"echo $$ > \""+scriptName+".pid\"\n" +
					"bash "+scriptName+".sh");
			fw.close();
			ps1.setExecutable(true);
			charset = Charset.defaultCharset(); // on Linux, the file is probably using some 8-bit charset

			command.add("setsid"); // start new process group so we can kill it at once
			command.add(ps1.getAbsolutePath());
		}
		
		ProcessBuilder pb = new ProcessBuilder(command).directory(exampleDir);
		pb.environment().remove("MEMBRANE_HOME");
		pb.redirectError(Redirect.PIPE).redirectOutput(Redirect.PIPE).redirectInput(Redirect.PIPE);
		final Process p = pb.start();
		
		p.getOutputStream().close();
		
		ProcessStuff ps = new ProcessStuff(p);
		pids.put(p, ps);
		
		for (AbstractConsoleWatcher acw : consoleWatchers)
			ps.watchers.add(acw);
		
		ps.startOutputWatchers();

		// now wait for the PID to be written
		for (int i = 0; i < 101; i++) {
			if (i == 100)
				throw new RuntimeException("could not read PID file");
			Thread.sleep(100);
			File f = new File(exampleDir, scriptName + ".pid");
			if (!f.exists())
				continue;
			FileInputStream fr = new FileInputStream(f);
			try {
				String line = new BufferedReader(new InputStreamReader(fr, charset)).readLine();
				if (line == null)
					continue;
				pids.get(p).pid = Integer.parseInt(line);
				break;
			} catch (NumberFormatException e) {
				// ignore
			} finally {
				fr.close();
			}
		}
		
		Thread.sleep(1000); // wait for router to start

		return p;
	}
	
	protected void addConsoleWatcher(Process p, AbstractConsoleWatcher watcher) {
		ProcessStuff ps = pids.get(p);
		synchronized(ps.watchers) {
			ps.watchers.add(watcher);
		}
	}
	
	protected void killScript(Process p) throws InterruptedException, IOException {
		ProcessStuff ps = pids.get(p);
		
		// start the killer
		ArrayList<String> command = new ArrayList<String>();
		if (System.getProperty("os.name").contains("Windows")) {
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
		ProcessBuilder pb = new ProcessBuilder(command).inheritIO();
		pb.redirectInput(Redirect.PIPE).redirectError(Redirect.PIPE).redirectOutput(Redirect.PIPE);

		// wait for killer to complete
		Process killer = pb.start();
		ProcessStuff killerStuff = new ProcessStuff(killer);
		killerStuff.startOutputWatchers();
		killerStuff.waitFor();
		
		// wait for membrane to terminate
		ps.waitFor();
	}

	
	public static final void copyInputStream(InputStream in, OutputStream out)
			throws IOException {
		byte[] buffer = new byte[1024];
		int len;

		while ((len = in.read(buffer)) >= 0)
			out.write(buffer, 0, len);

		in.close();
		out.close();
	}

	public static final void unzip(File zip, File target) throws IOException {
		ZipFile zipFile = new ZipFile(zip);
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = (ZipEntry) entries.nextElement();
			if (entry.isDirectory()) {
				// Assume directories are stored parents first then children.
				// This is not robust, just for demonstration purposes.
				new File(target, entry.getName()).mkdir();
			} else {
				copyInputStream(zipFile.getInputStream(entry),
						new BufferedOutputStream(new FileOutputStream(new File(
								target, entry.getName()))));
			}
		}
		zipFile.close();
	}
}
