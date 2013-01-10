package com.predic8.membrane.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.support.AbstractRefreshableApplicationContext;

import com.predic8.membrane.annot.MCElement;

@MCElement(name="springContextReloader", group="basic")
public class SpringContextReloader implements LifecycleProcessor, ApplicationContextAware {
	
	private HotDeploymentThread hdt;
	
	public SpringContextReloader() {
		System.err.println("create");
	}
	
	TrackingFileSystemXmlApplicationContext applicationContext;
	
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		if (applicationContext instanceof TrackingFileSystemXmlApplicationContext)
			this.applicationContext = (TrackingFileSystemXmlApplicationContext) applicationContext;
		else
			throw new RuntimeException("ApplicationContext is not a TrackingFileSystemXmlApplicationContext. Please remove the <springContextReloader>.");
	}

	@Override
	public boolean isRunning() {
		return hdt != null;
	}

	@Override
	public void start() {
		System.err.println("start");
		hdt = new HotDeploymentThread(applicationContext);
		hdt.setFiles(applicationContext.getFiles());
		hdt.start();
	}

	@Override
	public void stop() {
		hdt.interrupt();
		hdt = null;
		System.err.println("stop");
	}

	@Override
	public void onClose() {
		System.err.println("close");
	}

	@Override
	public void onRefresh() {
		System.err.println("refresh");
	}

	public static class HotDeploymentThread extends Thread {

		private static Log log = LogFactory.getLog(HotDeploymentThread.class.getName());

		private List<FileInfo> files = new ArrayList<FileInfo>();
		
		public static class FileInfo {
			public String file;
			public long lastModified;
		}

		private AbstractRefreshableApplicationContext applicationContext;

		public HotDeploymentThread(AbstractRefreshableApplicationContext applicationContext) {
			super("Membrane Hot Deployment Thread");
			this.applicationContext = applicationContext;
		}

		public void setFiles(List<File> files) {
			this.files.clear();
			for (File file : files) {
				FileInfo fi = new FileInfo();
				fi.file = file.getAbsolutePath();
				this.files.add(fi);
			}
			updateLastModified();
		}

		private void updateLastModified() {
			for (FileInfo fi : files)
				fi.lastModified = new File(fi.file).lastModified();
		}

		private boolean configurationChanged() {
			for (FileInfo fi : files)
				if (new File(fi.file).lastModified() > fi.lastModified)
					return true;
			return false;
		}

		@Override
		public void run() {
			log.debug("Hot Deployment Thread started.");
			while (!isInterrupted()) {
				try {
					while (!configurationChanged()) {
						sleep(1000);
					}

					log.debug("configuration changed.");

					applicationContext.stop();
					applicationContext.refresh();
					applicationContext.start();

					updateLastModified();
				} catch (InterruptedException e) {				
				} catch (Exception e) {
					log.error("Could not redeploy.", e);
					updateLastModified();
				}
			}
			log.debug("Hot Deployment Thread interrupted.");
		}
	}

}
