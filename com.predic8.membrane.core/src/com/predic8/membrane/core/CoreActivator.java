package com.predic8.membrane.core;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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

	/**
	 * The constructor
	 */

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

		pluginLogger.log(new Status(IStatus.ERROR, CoreActivator.PLUGIN_ID, "File name is" + getConfigFileName()));

		//log.debug(getConfigFileName());

		try {
			if (fileExists()) {
				pluginLogger.log(new Status(IStatus.ERROR, CoreActivator.PLUGIN_ID, "Reading configuration from " + getConfigFileName()));

				log.debug("Reading configuration from " + getConfigFileName());
				Router.init(new UrlResource(getConfigFileName()));
			} else {
				log.debug("Reading configuration from configuration/monitor-beans.xml");
				pluginLogger.log(new Status(IStatus.ERROR, CoreActivator.PLUGIN_ID, "Reading configuration from configuration/monitor-beans.xml"));

				Router.init("configuration/monitor-beans.xml");
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

}
