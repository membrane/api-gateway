package com.predic8.membrane.core;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.springframework.context.support.AbstractRefreshableApplicationContext;

import com.predic8.membrane.annot.MCElement;

@MCElement(name="springContextReloader", group="basic")
public class SpringContextReloader implements Lifecycle, ApplicationContextAware {
	
	private HotDeploymentThread hdt;
	
	public SpringContextReloader() {
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
		hdt = new HotDeploymentThread(applicationContext);
		hdt.setFiles(applicationContext.getFiles());
		hdt.start();
	}

	@Override
	public void stop() {
		hdt.interrupt();
		hdt = null;
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

					// save proxies.xml filenames for all defined routers
					Map<String, String> proxyXmlFiles = new HashMap<String, String>();
					for (Map.Entry<String, Router> e : applicationContext.getBeansOfType(Router.class).entrySet())
						proxyXmlFiles.put(e.getKey(), e.getValue().getConfigurationManager().getFilename());
					
					applicationContext.stop();
					applicationContext.refresh();
					applicationContext.start();
					
					// restore proxies.xml filenames for all routers which still exist
					for (Map.Entry<String, Router> e : applicationContext.getBeansOfType(Router.class).entrySet())
						if (proxyXmlFiles.containsKey(e.getKey()))
							e.getValue().getConfigurationManager().loadConfiguration(proxyXmlFiles.get(e.getKey()));

					updateLastModified();
				} catch (InterruptedException e) {				
				} catch (Exception e) {
					log.error("Could not redeploy.", e);
					updateLastModified();
				}
			}
			log.debug("Spring Hot Deployment Thread interrupted.");
		}
	}

}
