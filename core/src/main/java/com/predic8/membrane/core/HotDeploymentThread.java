package com.predic8.membrane.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.AbstractRefreshableApplicationContext;

public class HotDeploymentThread extends Thread {

	private static Log log = LogFactory.getLog(HotDeploymentThread.class.getName());

	private List<HotDeploymentThread.FileInfo> files = new ArrayList<HotDeploymentThread.FileInfo>();
	
	private static class FileInfo {
		public String file;
		public long lastModified;
	}

	protected AbstractRefreshableApplicationContext applicationContext;

	public HotDeploymentThread(AbstractRefreshableApplicationContext applicationContext) {
		super("Membrane Hot Deployment Thread");
		this.applicationContext = applicationContext;
	}

	public void setFiles(List<File> files) {
		this.files.clear();
		for (File file : files) {
			HotDeploymentThread.FileInfo fi = new FileInfo();
			fi.file = file.getAbsolutePath();
			this.files.add(fi);
		}
		updateLastModified();
	}

	private void updateLastModified() {
		for (HotDeploymentThread.FileInfo fi : files)
			fi.lastModified = new File(fi.file).lastModified();
	}

	private boolean configurationChanged() {
		for (HotDeploymentThread.FileInfo fi : files)
			if (new File(fi.file).lastModified() > fi.lastModified)
				return true;
		return false;
	}

	@Override
	public void run() {
		log.debug("Spring Hot Deployment Thread started.");
		OUTER:
		while (!isInterrupted()) {
			try {
				while (!configurationChanged()) {
					sleep(1000);
					if (isInterrupted())
						break OUTER;
				}

				log.debug("spring configuration changed.");

				reload();

				updateLastModified();
			} catch (InterruptedException e) {				
			} catch (Exception e) {
				log.error("Could not redeploy.", e);
				updateLastModified();
			}
		}
		log.debug("Spring Hot Deployment Thread interrupted.");
	}

	protected void reload() throws Exception {
		applicationContext.stop();
		applicationContext.refresh();
		applicationContext.start();
	}
}