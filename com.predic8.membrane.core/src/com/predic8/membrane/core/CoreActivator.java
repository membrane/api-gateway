package com.predic8.membrane.core;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
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

	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

		pluginLogger = CoreActivator.getDefault().getLog();

		error("File name is" + getConfigFileName());
		
		try {
			if (ClassloaderUtil.fileExists(getConfigFileName())) {
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
		
		URLClassLoader extLoader = ClassloaderUtil.getExternalClassloader(getProjectRoot());
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

	private String getConfigFileName() throws IOException {
		return getProjectRoot() + System.getProperty("file.separator") + "configuration" + System.getProperty("file.separator") + "monitor-beans.xml";
	}

	private String getProjectRoot() throws IOException {
		return new File(FileLocator.resolve(CoreActivator.getDefault().getBundle().getEntry("/")).getPath()).getParentFile().getParentFile().getPath();
	}
	
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	
	public static CoreActivator getDefault() {
		return plugin;
	}

}
