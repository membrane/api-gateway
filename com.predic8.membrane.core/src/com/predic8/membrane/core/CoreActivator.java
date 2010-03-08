package com.predic8.membrane.core;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;
import org.springframework.core.io.UrlResource;

/**
 * The activator class controls the plug-in life cycle
 */
public class CoreActivator extends Plugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.predic8.membrane.core";

	private static Log log = LogFactory.getLog(CoreActivator.class.getName());

	private static ILog pluginLogger;

	// The shared instance
	private static CoreActivator plugin;

	
	public CoreActivator() {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.Plugins#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

		pluginLogger = CoreActivator.getDefault().getLog();

		error("File name is" + getConfigFileName());
		
		try {
			if (fileExists()) {
				readBeanConfigWhenStartedAsProduct();
			} else {
				readBeanConfigWhenStartedInEclipse();
			}
		} catch (Exception e1) {
			
			e1.printStackTrace();
		}

		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					Router.getInstance().getConfigurationManager().loadConfiguration(System.getProperty("user.home") + System.getProperty("file.separator") + ".membrane.xml");
				} catch (Exception e) {
					// we ignore this exception because the monitor can start up
					// without loading a rules configuration
					// we need to throw an exception because the router must display an error message
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	private void readBeanConfigWhenStartedAsProduct() throws IOException, MalformedURLException {
		error("Reading configuration from " + getConfigFileName());
		log.debug("Reading configuration from " + getConfigFileName());
		
		URLClassLoader extLoader = getExternalClassloader();
		try {
			Class clazz = extLoader.loadClass("c.NewServiceLocator");
			error("Clazz object loaded with class loader: " + clazz.getClassLoader());
			
		} catch (ClassNotFoundException e) {
			error("class not found exception thrown: " + e.getMessage());
		}
		
		Router.init(new UrlResource(getConfigFileName()), extLoader );
	}

	private void readBeanConfigWhenStartedInEclipse() throws MalformedURLException {
		log.debug("Reading configuration from configuration/monitor-beans.xml");
		error("Reading configuration from configuration/monitor-beans.xml");

		Router.init("configuration/monitor-beans.xml", this.getClass().getClassLoader());
	}

	private void error(String message) {
		pluginLogger.log(new Status(IStatus.ERROR, CoreActivator.PLUGIN_ID, message));
	}

	private URLClassLoader getExternalClassloader() {
		try {
			
			List<URL> urls = getJarUrls(getFullQualifiedFolderName("lib"));
			urls.add(new URL(getFullQualifiedFolderName("classes")));
			
			return new URLClassLoader( urls.toArray(new URL[urls.size()]) , getClass().getClassLoader() );
		
		} catch (Exception e) {
			e.printStackTrace();
			error("Creation of external classloader failed." + e);
			System.exit(1);
		} 
		return null;
	}

	private boolean fileExists() throws MalformedURLException, IOException {
		String configFileName = getConfigFileName();
		log.debug(configFileName);
		if (configFileName.startsWith("file:"))
			return new File(new URL(configFileName).getPath()).exists();
		return new File(configFileName).exists();
	}

	private String getConfigFileName() throws IOException {
		return getProjectRoot() + System.getProperty("file.separator") + "configuration" + System.getProperty("file.separator") + "monitor-beans.xml";
	}

	private String getFullQualifiedFolderName(String folder) throws IOException {
		return getProjectRoot() + System.getProperty("file.separator") + folder + System.getProperty("file.separator");
	}
	
	private String getProjectRoot() throws IOException {
		return new File(FileLocator.resolve(CoreActivator.getDefault().getBundle().getEntry("/")).getPath()).getParentFile().getParentFile().getPath();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static CoreActivator getDefault() {
		return plugin;
	}
	
	public List<URL> getJarUrls(String folder) {
		
		if (folder.startsWith("file:"))
			folder = folder.substring(6);
		
		File file = new File(folder);
		if (!file.isDirectory()) 
			return new ArrayList<URL>();
		
		String[] jars = file.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".jar");
			}
		});
		
		List<URL> urlList = new ArrayList<URL>();
		for (int i = 0; i < jars.length; i ++) {
			try {
				urlList.add(new URL("file:" + folder + jars[i]));
			} catch (MalformedURLException e) {
				error(e.getMessage());
			}
		}
		
		return urlList;
		
	}

}
